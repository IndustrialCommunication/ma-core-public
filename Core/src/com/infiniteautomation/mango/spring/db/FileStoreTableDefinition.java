/**
 * Copyright (C) 2020  Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.spring.db;

import java.util.List;

import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

/**
 * @author Terry Packer
 *
 */
@Component
public class FileStoreTableDefinition extends AbstractBasicTableDefinition {
    public static final String TABLE_NAME = "fileStores";
    
    public FileStoreTableDefinition() {
        super(DSL.table(TABLE_NAME), DSL.name("fs"));
    }

    @Override
    protected void addFields(List<Field<?>> fields) {
        fields.add(DSL.field(DSL.name("storeName"), SQLDataType.VARCHAR(100).nullable(false)));
    }
    
    
}
