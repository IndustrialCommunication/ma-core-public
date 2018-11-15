/**
 * Copyright (C) 2018  Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.validation.Constraint;
import javax.validation.Payload;

/**
 * Is a data point id or Xid valid 
 * - does it exist
 * - TODO permissions optionally
 * 
 * @author Terry Packer
 *
 */
@Constraint(validatedBy = ValidIntStringPairValidator.class)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIntStringPair {

    //TODO Check permissions
//    boolean checkReadPermission() default false;
//    boolean checkSetPermission() default false;
    
    /**
     * Does this represent a Script Context variable
     * @return
     */
    boolean isScriptContextVariable() default false;
    String message() default "validate.invalidValue";

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };
}
