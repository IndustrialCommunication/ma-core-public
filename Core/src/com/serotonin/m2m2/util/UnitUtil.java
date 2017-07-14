/*
 * Copyright (C) 2013 Infinite Automation and Deltamation Software. All rights reserved.
 * @author Jared Wiltshire
 */

package com.serotonin.m2m2.util;

import java.text.ParseException;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;

import javax.measure.quantity.Energy;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;
import javax.measure.unit.UnitFormat;

import com.serotonin.m2m2.Common;

/**
 * Copyright (C) 2013 Deltamation Software. All rights reserved.
 * 
 * @author Jared Wiltshire
 */
public class UnitUtil {
    static final UnitFormat localFormat = UnitFormat.getInstance(Common.getLocale());
    static final UnitFormat ucumFormat = UnitFormat.getInstance();//UnitFormat.getUCUMInstance();
    static final List<String> addedUnitLabels = new ArrayList<String>();

    public static final Unit<Energy> BTU = SI.JOULE.times(1055.05585262D);
    public static final Unit<Energy> THERM = BTU.times(100000);
    public static final Unit<?> PSI = NonSI.POUND_FORCE.divide(NonSI.INCH.pow(2));

    static {
        // register some labels
        addLabel(BTU, "btu");
        addLabel(THERM, "thm");
        addLabel(PSI, "psi");
        addLabel(NonSI.FOOT.pow(3).divide(NonSI.MINUTE), "cfm");
        
        addAlias(NonSI.REVOLUTION.divide(NonSI.MINUTE), "rpm");
        addAlias(Unit.ONE.divide(1000000), "ppm");
        addAlias(Unit.ONE.divide(1000000000), "ppb");
        
        addAlias(NonSI.GALLON_LIQUID_US.divide(NonSI.MINUTE), "gpm");
        
        addAlias(SI.WATT.times(NonSI.HOUR), "Wh");
        addAlias(SI.KILO(SI.WATT).times(NonSI.HOUR), "kWh");
        addAlias(SI.MEGA(SI.WATT).times(NonSI.HOUR), "MWh");

        addAlias(SI.VOLT.times(SI.AMPERE), "VAR");
        addAlias(SI.KILO(SI.VOLT).times(SI.AMPERE), "kVAR");
        addAlias(SI.MEGA(SI.VOLT).times(SI.AMPERE), "MVAR");
        
        addAlias(SI.VOLT.times(SI.AMPERE), "VA");
        addAlias(SI.KILO(SI.VOLT).times(SI.AMPERE), "kVA");
        addAlias(SI.MEGA(SI.VOLT).times(SI.AMPERE), "MVA");
        
        addAlias(Unit.ONE, "pf");
        
        //Define any aliases
        addAlias(SI.CELSIUS, "Celsius"); // easier to type
        addAlias(NonSI.FAHRENHEIT, "Fahrenheit");
    }
    
    public static void addLabel(Unit<?> unit, String label) {
        localFormat.label(unit, label);
        addedUnitLabels.add(label);
    }
    
    public static void addAlias(Unit<?> unit, String alias) {
        localFormat.alias(unit, alias);
        addedUnitLabels.add(alias);
    }

    @SuppressWarnings("deprecation")
    public static Unit<?> convertToUnit(int engineeringUnit) {
        Unit<?> unit = EngineeringUnits.conversionMap.get(engineeringUnit);
        return (unit == null) ? Unit.ONE : unit;
    }

    public static String formatLocal(Unit<?> unit) {
        return localFormat.format(unit);
    }

    public static Unit<?> parseLocal(String unit) throws IllegalArgumentException {
        try {
            return localFormat.parseProductUnit(unit, new ParsePosition(0));
        }
        catch (ParseException e) {
            try {
                return ucumFormat.parseProductUnit(unit, new ParsePosition(0));
            }
            catch (ParseException e2) {
            	//This has been changed to now throw an exception for better validation
                throw new IllegalArgumentException(e);
            }
        }
    }

    public static String formatUcum(Unit<?> unit) {
        return ucumFormat.format(unit);
    }

    public static Unit<?> parseUcum(String unit) throws IllegalArgumentException {
        try {
            return ucumFormat.parseProductUnit(unit, new ParsePosition(0));
        }
        catch (ParseException e) {
            try {
                return localFormat.parseProductUnit(unit, new ParsePosition(0));
            }
            catch (ParseException e2) {
            	//This has been changed to now throw an exception for better validation
                throw new IllegalArgumentException(e);
                //return Unit.ONE;
            }
        }
    }
    
    public static List<String> getAddedUnitLabels() {
    	return addedUnitLabels;
    }
}
