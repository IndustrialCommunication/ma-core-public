/**
 * Copyright (C) 2015 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.infiniteautomation.mango.db.query;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.apache.commons.beanutils.PropertyUtils;

import com.serotonin.ShouldNeverHappenException;


/**
 * @author Terry Packer
 *
 */
public class QueryComparison {
	
	private String attribute;
	private ComparisonEnum comparisonType;
	private List<Object> arguments;
	
	public QueryComparison() { }
	
	public QueryComparison(String attribute, ComparisonEnum comparisonType, List<Object> arguments){
		this.attribute = attribute;
		this.comparisonType = comparisonType;
		this.arguments = arguments;
	}

	public String getAttribute() {
		return attribute;
	}

	public void setAttribute(String attribute) {
		this.attribute = attribute;
	}
	
	public ComparisonEnum getComparison(){
		return this.comparisonType;
	}
	
	public Object getArgument(int index){
		return this.arguments.get(0);
	}
	
	public List<Object> getArguments(){
		return this.arguments;
	}

	/**
	 * Apply this condition to an object that contains the 
	 * attribute to apply against.  
	 * 
	 * This method uses reflection to extract the value and then
	 * apply the condition.
	 * 
	 * @param instance
	 * @return
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws SecurityException
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 */
	public boolean apply(Object instance){
		
		try {
			Object value = PropertyUtils.getProperty(instance, attribute);
			return this.compare(value);
		} catch (IllegalAccessException | InvocationTargetException
				| NoSuchMethodException e) {
			return false;
		}
		
	}
	
	/**
	 * Compare the value by first determining its type
	 * @param value
	 * @return
	 */
	public boolean compare(Object value){
		
		if(value instanceof Integer){
			return compareInteger((Integer)value);
		}else if(value instanceof Double){
			return compareDouble((Double)value);
		}else if(value instanceof Long){
			return compareLong((Long)value);
		}else if(value instanceof String){
			return compareString((String)value);
		}else if(value instanceof Boolean){
			return compareBoolean((Boolean)value);
		}else{
			throw new ShouldNeverHappenException("Unsupported class type: " + value.getClass().getCanonicalName());
		}
	}
	
	public boolean compareInteger(Integer thisValue){
		try{
			Integer value = castToInteger(this.arguments.get(0));
			switch(comparisonType){
				case GREATER_THAN:
					return thisValue > value;
				case GREATER_THAN_EQUAL_TO:
					return thisValue >= value;
				case LESS_THAN:
					return thisValue < value;
				case LESS_THAN_EQUAL_TO:
					return thisValue <= value;
				case LIKE:
		    	case EQUAL_TO:
					return thisValue == value;
		    	case NOT_EQUAL_TO:
					return thisValue != value;
				default:
					throw new ShouldNeverHappenException("Unsupported comparisonType: " + getComparison());
			}
		}catch(NumberFormatException e){
			//Munchy munch
			return false;
		}
	}

	public boolean compareDouble(Double thisValue){
		try{
			Double value = castToDouble(this.arguments.get(0));
			switch(comparisonType){
				case GREATER_THAN:
					return thisValue > value;
				case GREATER_THAN_EQUAL_TO:
					return thisValue >= value;
				case LESS_THAN:
					return thisValue < value;
				case LESS_THAN_EQUAL_TO:
					return thisValue <= value;
				case LIKE:
		    	case EQUAL_TO:
					return thisValue == value;
		    	case NOT_EQUAL_TO:
					return thisValue != value;
				default:
					throw new ShouldNeverHappenException("Unsupported comparisonType: " + getComparison());
			}
		}catch(NumberFormatException e){
			//Munchy munch
			return false;
		}
	}
	
	public boolean compareLong(Long thisValue){
		try{
			Long value = castToLong(this.arguments.get(0));
			switch(comparisonType){
				case GREATER_THAN:
					return thisValue > value;
				case GREATER_THAN_EQUAL_TO:
					return thisValue >= value;
				case LESS_THAN:
					return thisValue < value;
				case LESS_THAN_EQUAL_TO:
					return thisValue <= value;
				case LIKE:
		    	case EQUAL_TO:
					return thisValue == value;
		    	case NOT_EQUAL_TO:
					return thisValue != value;
				default:
					throw new ShouldNeverHappenException("Unsupported comparisonType: " + getComparison());
			}
		}catch(NumberFormatException e){
			//Munchy munch
			return false;
		}
	}
	
	public boolean compareString(String thisValue){
		
		String value = castToString(this.arguments.get(0));
		
		switch(comparisonType){
			case GREATER_THAN:
				return thisValue.compareTo(value) > 0;
			case GREATER_THAN_EQUAL_TO:
				return thisValue.compareTo(value) >= 0;
			case LESS_THAN:
				return thisValue.compareTo(value) < 0;
			case LESS_THAN_EQUAL_TO:
				return thisValue.compareTo(value) <= 0;
			case LIKE:
				//Create regex by simply replacing % by .*
				String regex = thisValue.replace("%", ".*");
				return thisValue.matches(regex);
	    	case EQUAL_TO:
	    		return thisValue.equals(value);
	    	case NOT_EQUAL_TO:
	    		return !thisValue.equals(value);
			default:
				throw new ShouldNeverHappenException("Unsupported comparisonType: " + getComparison());
		}
	}
	public boolean compareBoolean(Boolean thisValue){
		try{
			
			Boolean value = castToBoolean(this.arguments.get(0));
			switch(comparisonType){
				case GREATER_THAN:
					return thisValue.compareTo(value) > 0;
				case GREATER_THAN_EQUAL_TO:
					return thisValue.compareTo(value) >= 0;
				case LESS_THAN:
					return thisValue.compareTo(value) < 0;
				case LESS_THAN_EQUAL_TO:
					return thisValue.compareTo(value) <= 0;
				case LIKE:
		    	case EQUAL_TO:
					return thisValue == value;
		    	case NOT_EQUAL_TO:
					return thisValue != value;
				default:
					throw new ShouldNeverHappenException("Unsupported comparisonType: " + getComparison());
			}
		}catch(NumberFormatException e){
			//Munchy munch
			return false;
		}
	}

	/**
	 * @param object
	 * @return
	 */
	private Integer castToInteger(Object object) {
		if(object instanceof Integer)
			return (Integer)object;
		else if(object instanceof String)
			return Integer.parseInt((String)object);
		else if(object instanceof Number)
			return ((Number)object).intValue();
		else
			return null;
	}

	
	/**
	 * @param object
	 * @return
	 */
	private Long castToLong(Object object) {
		if(object instanceof Long)
			return (Long)object;
		else if(object instanceof String)
			return Long.parseLong((String)object);
		else if(object instanceof Number)
			return ((Number)object).longValue();
		else
			return null;
	}
	
	/**
	 * @param object
	 * @return
	 */
	private Double castToDouble(Object object) {
		if(object instanceof Double)
			return (Double)object;
		else if(object instanceof String)
			return Double.parseDouble((String)object);
		else if(object instanceof Number)
			return ((Number)object).doubleValue();
		else
			return null;
	}
	
	/**
	 * @param object
	 * @return
	 */
	private String castToString(Object object) {
		return object.toString();
	}
	
	/**
	 * @param object
	 * @return
	 */
	private Boolean castToBoolean(Object object) {
		if(object instanceof Boolean)
			return (Boolean)object;
		else if(object instanceof String)
			return Boolean.parseBoolean((String)object);
		else if(object instanceof Number)
			return (((Number)object).equals(new Integer(1))) ? true : false;
		else
			return false;
	}

	public String toString(){
		StringBuilder builder = new StringBuilder();
		
		builder.append(this.attribute);
		builder.append(" ");

		builder.append(this.comparisonType);
		builder.append(" ");

		for(Object o : this.arguments)
			builder.append(o + " ");
		
		return builder.toString();
	}
}
