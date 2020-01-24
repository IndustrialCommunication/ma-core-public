/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2.vo.publish;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import com.serotonin.json.JsonException;
import com.serotonin.json.JsonReader;
import com.serotonin.json.ObjectWriter;
import com.serotonin.json.spi.JsonSerializable;
import com.serotonin.json.type.JsonObject;
import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.i18n.TranslatableJsonException;

/**
 * @author Matthew Lohbihler
 */
abstract public class PublishedPointVO implements Serializable, JsonSerializable {
    private int dataPointId;

    public int getDataPointId() {
        return dataPointId;
    }

    public void setDataPointId(int dataPointId) {
        this.dataPointId = dataPointId;
    }

    //
    //
    // Serialization
    //
    private static final long serialVersionUID = -1;
    private static final int version = 1;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(version);
        out.writeInt(dataPointId);
    }

    private void readObject(ObjectInputStream in) throws IOException {
        int ver = in.readInt();

        // Switch on the version of the class so that version changes can be elegantly handled.
        if (ver == 1) {
            dataPointId = in.readInt();
        }
    }

    @Override
    public void jsonWrite(ObjectWriter writer) throws IOException, JsonException {
        String xid = DataPointDao.getInstance().getXidById(dataPointId);
        writer.writeEntry("dataPointId", xid);
    }

    @Override
    public void jsonRead(JsonReader reader, JsonObject jsonObject) throws JsonException {
        String xid = jsonObject.getString("dataPointId");
        if (xid == null)
            throw new TranslatableJsonException("emport.error.publishedPoint.missing", "dataPointId");
        
        Integer id = DataPointDao.getInstance().getIdByXid(xid);
        if (id == null)
            throw new TranslatableJsonException("emport.error.missingPoint", xid);
        dataPointId = id;
    }
}
