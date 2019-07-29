/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2.rt.dataImage;

import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.i18n.Translations;
import com.serotonin.m2m2.rt.dataImage.types.DataValue;

/**
 * This class provides a way of arbitrarily annotating a PointValue. Point value annotations should not be confused with
 * Java annotations. A point value annotation will typically explain the source of the value when it did not simply come
 * from data source.
 * 
 * @see SetPointSource
 * @author Matthew Lohbihler
 */
public class AnnotatedPointValueTime extends PointValueTime implements IAnnotated {
    private static final long serialVersionUID = -1;
    private final TranslatableMessage sourceMessage;

    public AnnotatedPointValueTime(DataValue value, long time, TranslatableMessage sourceMessage) {
        super(value, time);
        this.sourceMessage = sourceMessage;
    }
    
    @Override
    public TranslatableMessage getSourceMessage() {
        return sourceMessage;
    }
    @Override
    public String getAnnotation(Translations translations) {
        if(sourceMessage != null)
         return sourceMessage.translate(translations);
        else
            return null;
    }
}
