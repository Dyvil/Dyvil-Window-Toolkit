package dyvil.tools.compiler.ast.dwt;

import java.util.Map;

import dyvil.collection.List;
import dyvil.collection.mutable.ArrayList;
import dyvil.reflect.Opcodes;
import dyvil.string.StringUtils;
import dyvil.tools.asm.Label;
import dyvil.tools.compiler.ast.classes.IClass;
import dyvil.tools.compiler.ast.context.IContext;
import dyvil.tools.compiler.ast.expression.IValue;
import dyvil.tools.compiler.ast.expression.IValueList;
import dyvil.tools.compiler.ast.expression.IValueMap;
import dyvil.tools.compiler.ast.expression.Value;
import dyvil.tools.compiler.ast.generic.ITypeContext;
import dyvil.tools.compiler.ast.member.INamed;
import dyvil.tools.compiler.ast.member.Name;
import dyvil.tools.compiler.ast.method.IConstructor;
import dyvil.tools.compiler.ast.method.IMethod;
import dyvil.tools.compiler.ast.parameter.EmptyArguments;
import dyvil.tools.compiler.ast.parameter.SingleArgument;
import dyvil.tools.compiler.ast.structure.IClassCompilableList;
import dyvil.tools.compiler.ast.type.IType;
import dyvil.tools.compiler.ast.type.Types;
import dyvil.tools.compiler.backend.MethodWriter;
import dyvil.tools.compiler.backend.exception.BytecodeException;
import dyvil.tools.compiler.config.Formatting;
import dyvil.tools.compiler.lexer.marker.MarkerList;
import dyvil.tools.compiler.lexer.position.ICodePosition;
import dyvil.tools.compiler.util.Util;

public class DWTNode extends Value implements INamed, IValueMap
{
	public static final int	NODE		= 256;
	public static final int	LIST		= 257;
	public static final int	REFERENCE	= 258;
	
	public DWTNode parent;
	
	public Name					name;
	public String				fullName;
	public IType				type;
	public List<DWTProperty>	properties	= new ArrayList();
	
	protected IClass	theClass;
	protected IMethod	getter;
	
	private int varIndex;
	
	public DWTNode()
	{
	}
	
	public DWTNode(ICodePosition position)
	{
		this.position = position;
	}
	
	public DWTNode(ICodePosition position, Name name)
	{
		this.position = position;
		this.name = name;
		this.fullName = name.qualified;
	}
	
	public void setParent(DWTNode parent)
	{
		this.fullName = parent.fullName + "$" + this.name;
		this.parent = parent;
	}
	
	@Override
	public int valueTag()
	{
		return NODE;
	}
	
	@Override
	public IType getType()
	{
		return this.type;
	}
	
	@Override
	public boolean isType(IType type)
	{
		return false;
	}
	
	@Override
	public IValue withType(IType type, ITypeContext typeContext, MarkerList markers, IContext context)
	{
		return this;
	}
	
	@Override
	public float getTypeMatch(IType type)
	{
		return 0;
	}
	
	@Override
	public void setName(Name name)
	{
		this.name = name;
	}
	
	@Override
	public Name getName()
	{
		return this.name;
	}
	
	@Override
	public void addValue(Name key, IValue value)
	{
		if (value.valueTag() == NODE)
		{
			((DWTNode) value).setParent(this);
		}
		this.properties.add(new DWTProperty(this, key, value));
	}
	
	@Override
	public IValue getValue(Name key)
	{
		return null;
	}
	
	public void addFields(Map<String, IType> fields)
	{
		fields.put(this.fullName, this.type);
		
		for (DWTProperty property : this.properties)
		{
			fields.put(property.fullName, property.value.getType());
		}
	}
	
	@Override
	public void resolveTypes(MarkerList markers, IContext context)
	{
		String s = "J" + StringUtils.toTitleCase(this.name.qualified);
		this.theClass = DWTFile.javaxSwing.resolveClass(s);
		
		if (this.theClass == null)
		{
			markers.add(this.position, "dwt.component.type", this.name, s);
			return;
		}
		
		this.type = this.theClass.getType();
		
		for (DWTProperty property : this.properties)
		{
			property.value.resolveTypes(markers, context);
		}
	}
	
	@Override
	public DWTNode resolve(MarkerList markers, IContext context)
	{
		for (DWTProperty property : this.properties)
		{
			String key = property.key.qualified;
			IValue value = property.value;
			int type = value.valueTag();
			if (type == LIST)
			{
				for (IValue v : (IValueList) value)
				{
					String s1 = Util.getAdder(key);
					IMethod m = IContext.resolveMethod(this.theClass, this, Name.getQualified(s1), new SingleArgument(value));
					if (m != null)
					{
						value.resolve(markers, m);
						continue;
					}
					markers.add(v.getPosition(), "dwt.property.unknown", key, this.type.toString());
				}
			}
			else if (type == NODE)
			{
				DWTNode node = (DWTNode) value;
				IClass iclass = node.theClass;
				node.resolve(markers, context);
				
				if (iclass == null)
				{
					continue;
				}
				
				IMethod getter = IContext.resolveMethod(this.theClass, this, Name.getQualified(Util.getGetter(key)), EmptyArguments.INSTANCE);
				if (getter != null)
				{
					node.getter = getter;
					continue;
				}
				
				IConstructor match = IContext.resolveConstructor(this.theClass, EmptyArguments.INSTANCE);
				if (match == null)
				{
					markers.add(value.getPosition(), "dwt.component.constructor");
				}
			}
			else
			{
				String s1 = Util.getSetter(key);
				IMethod m = IContext.resolveMethod(this.theClass, this, Name.getQualified(s1), new SingleArgument(value));
				if (m != null)
				{
					property.setter = m;
					continue;
				}
				markers.add(value.getPosition(), "dwt.property.unknown", key, this.type.toString());
			}
		}
		return this;
	}
	
	@Override
	public void checkTypes(MarkerList markers, IContext context)
	{
	}
	
	@Override
	public void check(MarkerList markers, IContext context)
	{
	}
	
	@Override
	public IValue foldConstants()
	{
		return this;
	}
	
	@Override
	public IValue cleanup(IContext context, IClassCompilableList compilableList)
	{
		return this;
	}
	
	@Override
	public void writeExpression(MethodWriter writer) throws BytecodeException
	{
		writer.writeVarInsn(Opcodes.ALOAD, this.varIndex);
	}
	
	@Override
	public void writeStatement(MethodWriter writer) throws BytecodeException
	{
	}
	
	public void write(String owner, MethodWriter writer) throws BytecodeException
	{
		String internal = this.type.getInternalName();
		String extended = "L" + internal + ";";
		Label start = new Label();
		Label end = new Label();
		
		int index = this.varIndex = writer.localCount();
		writer.writeLabel(start);
		if (this.getter != null)
		{
			// Getter
			this.getter.writeCall(writer, this.parent, EmptyArguments.INSTANCE, Types.VOID, 0);
		}
		else
		{
			// Constructor
			writer.writeTypeInsn(Opcodes.NEW, internal);
			writer.writeInsn(Opcodes.DUP);
			writer.writeInsn(Opcodes.DUP);
			writer.writeInvokeInsn(Opcodes.INVOKESPECIAL, internal, "<init>", "()V", false);
			writer.writeFieldInsn(Opcodes.PUTSTATIC, owner, this.fullName, extended);
		}
		
		writer.writeVarInsn(Opcodes.ASTORE, index);
		
		for (DWTProperty property : this.properties)
		{
			IMethod setter = property.setter;
			IValue value = property.value;
			if (setter != null)
			{
				writer.writeVarInsn(Opcodes.ALOAD, index);
				value.writeExpression(writer);
				writer.writeInsn(Opcodes.DUP);
				writer.writeFieldInsn(Opcodes.PUTSTATIC, owner, property.fullName, value.getType().getExtendedName());
				setter.writeCall(writer, null, EmptyArguments.INSTANCE, Types.VOID, 0);
			}
			else if (value.valueTag() == NODE)
			{
				((DWTNode) value).write(owner, writer);
			}
		}
		
		writer.writeLabel(end);
		writer.writeLocal(index, this.name.qualified, extended, null, start, end);
	}
	
	@Override
	public void toString(String prefix, StringBuilder buffer)
	{
		buffer.append(this.name).append('\n');
		buffer.append(prefix).append('{');
		String prefix1 = prefix + '\t';
		for (DWTProperty property : this.properties)
		{
			buffer.append('\n').append(prefix1).append(property.key).append(Formatting.Field.keyValueSeperator).append(' ');
			property.value.toString(prefix1, buffer);
		}
		buffer.append('\n').append(prefix).append('}');
	}
}
