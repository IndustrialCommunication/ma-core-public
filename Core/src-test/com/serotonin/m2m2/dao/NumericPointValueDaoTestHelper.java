/**
 * @copyright 2017 {@link http://infiniteautomation.com|Infinite Automation Systems, Inc.} All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.dao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableLong;
import org.junit.Assert;

import com.infiniteautomation.mango.db.query.BookendQueryCallback;
import com.infiniteautomation.mango.db.query.PVTQueryCallback;
import com.serotonin.m2m2.db.dao.PointValueDao;
import com.serotonin.m2m2.rt.dataImage.IdPointValueTime;
import com.serotonin.m2m2.rt.dataImage.PointValueTime;

/**
 * 
 * Class to keep all the test logic in one place so we 
 * can test the various PointValueDao implementations equally.
 * 
 * The test 
 *
 * @author Terry Packer
 */
public class NumericPointValueDaoTestHelper {
    
    protected Integer seriesId = 1;
    protected Integer seriesId2 = 2;
    protected List<Integer> ids;
    protected Map<Integer, List<PointValueTime>> data;
    protected static long startTs;
    protected static long endTs;
    protected long series2StartTs;
    protected long series2EndTs; 
    protected int totalSampleCount;
    protected final PointValueDao dao;
    
    public NumericPointValueDaoTestHelper(PointValueDao dao) {
        this.dao = dao;
        
        this.ids = new ArrayList<>();
        this.ids.add(seriesId);
        this.ids.add(seriesId2);
        this.data = new HashMap<>();
    }
    
    /**
     * Insert some test data.
     * Call before every test.
     */
    public void before() {
        //Start back 30 days
        endTs = System.currentTimeMillis();
        startTs = endTs - (30l * 24l * 60l * 60l * 1000l);
        
        for(Integer id : ids)
            this.data.put(id, new ArrayList<>());
        
        //Insert a few samples for series 2 before our time
        series2StartTs = startTs - (1000 * 60 * 15);
        long time = series2StartTs;
        PointValueTime p2vt = new PointValueTime(-3.0, time);
        this.dao.savePointValueSync(seriesId2, p2vt, null);
        this.data.get(seriesId2).add(p2vt);
        
        time = startTs - (1000 * 60 * 10);
        p2vt = new PointValueTime(-2.0, time);
        this.dao.savePointValueSync(seriesId2, p2vt, null);
        this.data.get(seriesId2).add(p2vt);
        
        time = startTs - (1000 * 60 * 5);
        p2vt = new PointValueTime(-1.0, time);
        this.dao.savePointValueSync(seriesId2, p2vt, null);
        this.data.get(seriesId2).add(p2vt);
        
        time = startTs;
        //Insert a sample every 5 minutes
        double value = 0.0;
        while(time < endTs){
            PointValueTime pvt = new PointValueTime(value, time);
            this.data.get(seriesId).add(pvt);
            this.data.get(seriesId2).add(pvt);
            this.dao.savePointValueSync(seriesId, pvt, null);
            this.dao.savePointValueSync(seriesId2, pvt, null);
            time = time + 1000 * 60 * 5;
            totalSampleCount++;
            value++;
        }
        
        //Add a few more samples for series 2 after our time
        p2vt = new PointValueTime(value++, time);
        this.dao.savePointValueSync(seriesId2, p2vt, null);
        this.data.get(seriesId2).add(p2vt);
        
        time = time + (1000 * 60 * 5);
        p2vt = new PointValueTime(value++, time);
        this.dao.savePointValueSync(seriesId2, p2vt, null);
        this.data.get(seriesId2).add(p2vt);
        
        time = time + (1000 * 60 * 5);
        p2vt = new PointValueTime(value++, time);
        this.dao.savePointValueSync(seriesId2, p2vt, null);
        this.data.get(seriesId2).add(p2vt);
        this.series2EndTs = time;
    }

    /**
     * Call after every test
     */
    public void after() {
        this.dao.deleteAllPointData();
    }
    

    
    /* Latest Multiple w/ callback Test Methods */
    public void testLatestExceptionInCallback () {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(endTs);
        this.dao.getLatestPointValues(ids, endTs, false, null, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = data.get(seriesId).size() - 1;
            int seriesId2Counter = data.get(seriesId2).size() - 4; //Start before last 3 samples (extra)
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() > timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter--;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter--;
                }
                if(count.getValue() == 20)
                    throw new IOException("Exception Test");
            }
            
        });
        //Total is all samples + the extra 3 at the beginning of series2
        Assert.assertEquals(new Integer(20) , count.getValue());
    }
    
    public void testLatestNoDataInBothSeries() {
        MutableInt count = new MutableInt();
        this.dao.getLatestPointValues(ids, series2StartTs, false, null, new PVTQueryCallback<IdPointValueTime>() {
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                count.increment();
            }
            
        });
        Assert.assertEquals(new Integer(0), count.getValue());
    }

    public void testLatestNoDataInOneSeries() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(startTs);
        this.dao.getLatestPointValues(ids, startTs, false, null, new PVTQueryCallback<IdPointValueTime>() {
            int seriesId2Counter = 2;
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() > timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter--;
                }else {
                    Assert.fail("Should not get data for series 1");
                }
            }
            
        });
        //Total is all samples + the extra 3 at the beginning of series2
        Assert.assertEquals(new Integer(3) , count.getValue());
    }
    
    public void testLatestMultiplePointValuesNoLimit() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(endTs);
        this.dao.getLatestPointValues(ids, endTs, false, null, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = data.get(seriesId).size() - 1;
            int seriesId2Counter = data.get(seriesId2).size() - 4; //Start before last 3 samples (extra)
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() > timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter--;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter--;
                }
            }
            
        });
        //Total is all samples + the extra 3 at the beginning of series2
        Assert.assertEquals(new Integer(totalSampleCount * 2 + 3) , count.getValue());
    }
    
    /**
     * Test where point 2 has more data than point 1
     */
    public void testLatestMultiplePointValuesNoLimitOffsetSeries() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(series2EndTs + 1);
        this.dao.getLatestPointValues(ids, series2EndTs + 1, false, null, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = data.get(seriesId).size() - 1;
            int seriesId2Counter = data.get(seriesId2).size() - 1;
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() > timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter--;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter--;
                }
            }
            
        });
        //Total is all samples + the extra 3 at the beginning of series2
        Assert.assertEquals(new Integer(totalSampleCount * 2 + 6) , count.getValue());
    }
    
    public void testLatestMultiplePointValuesOrderByIdNoLimit() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp1 = new MutableLong(endTs);
        MutableLong timestamp2 = new MutableLong(endTs);
        this.dao.getLatestPointValues(ids, endTs, true, null, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = data.get(seriesId).size() - 1;
            int seriesId2Counter = data.get(seriesId2).size() - 4; //Start before last 3 samples (extra)
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(index < data.get(seriesId).size()) {
                    //Should be first id
                    Assert.assertEquals((int)seriesId, value.getId());
                }else {
                    Assert.assertEquals((int)seriesId2, value.getId());
                }
                if(value.getId() == seriesId2) {
                    if(value.getTime() > timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter--;
                }else {
                    if(value.getTime() > timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter--;
                }
            }
            
        });
        //Total is all samples + the extra 3 at the beginning of series2
        Assert.assertEquals(new Integer(totalSampleCount * 2 + 3) , count.getValue());

    }
    
    public void testLatestMultiplePointValuesOrderByIdNoLimitOffsetSeries() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp1 = new MutableLong(series2EndTs + 1);
        MutableLong timestamp2 = new MutableLong(series2EndTs + 1);
        this.dao.getLatestPointValues(ids, series2EndTs + 1, true, null, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = data.get(seriesId).size() - 1;
            int seriesId2Counter = data.get(seriesId2).size() - 1; 
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(index < data.get(seriesId).size()) {
                    //Should be first id
                    Assert.assertEquals((int)seriesId, value.getId());
                }else {
                    Assert.assertEquals((int)seriesId2, value.getId());
                }
                if(value.getId() == seriesId2) {
                    if(value.getTime() > timestamp2.getValue())
                        Assert.fail("Timestamp out of order");
                    timestamp2.setValue(value.getTime());
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter--;
                }else {
                    if(value.getTime() > timestamp1.getValue())
                        Assert.fail("Timestamp out of order");
                    timestamp1.setValue(value.getTime());
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter--;
                }
            }
            
        });
        //Total is all samples + the extra 3 at the beginning of series2
        Assert.assertEquals(new Integer(totalSampleCount * 2 + 6) , count.getValue());

    }
    
    public void testLatestMultiplePointValuesLimit() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(endTs);
        this.dao.getLatestPointValues(ids, endTs, false, 20, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = data.get(seriesId).size() - 1;
            int seriesId2Counter = data.get(seriesId2).size() - 4;
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() > timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter--;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter--;
                }
            }
            
        });
        Assert.assertEquals(new Integer(20), count.getValue());
    }
    
    public void testLatestMultiplePointValuesLimitOffsetSeries() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(series2EndTs + 1);
        this.dao.getLatestPointValues(ids, series2EndTs + 1, false, 20, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = data.get(seriesId).size() - 1;
            int seriesId2Counter = data.get(seriesId2).size() - 1;
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() > timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter--;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter--;
                }
            }
            
        });
        Assert.assertEquals(new Integer(20), count.getValue());
    }
    
    public void testLatestMultiplePointValuesOrderByIdLimit() {
        MutableInt count1 = new MutableInt();
        MutableInt count2 = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp1 = new MutableLong(endTs);
        MutableLong timestamp2 = new MutableLong(endTs);
        this.dao.getLatestPointValues(ids, endTs, true, 20, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = data.get(seriesId).size() - 1;
            int seriesId2Counter = data.get(seriesId2).size() - 4;
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(index < 20) {
                    //Should be first id
                    Assert.assertEquals((int)seriesId, value.getId());
                }else {
                    Assert.assertEquals((int)seriesId2, value.getId());
                }
                if(value.getId() == seriesId2) {
                    if(value.getTime() > timestamp2.getValue())
                        Assert.fail("Timestamp out of order");
                    timestamp2.setValue(value.getTime());
                    count2.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter--;
                }else {
                    if(value.getTime() > timestamp1.getValue())
                        Assert.fail("Timestamp out of order");
                    timestamp1.setValue(value.getTime());
                    count1.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter--;
                }
            }
            
        });
        Assert.assertEquals(new Integer(20), count1.getValue());
        Assert.assertEquals(new Integer(20), count2.getValue());
    }
    
    public void testLatestMultiplePointValuesOrderByIdLimitOffsetSeries() {
        MutableInt count1 = new MutableInt();
        MutableInt count2 = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp1 = new MutableLong(series2EndTs + 1);
        MutableLong timestamp2 = new MutableLong(series2EndTs + 1);
        this.dao.getLatestPointValues(ids, series2EndTs + 1, true, 20, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = data.get(seriesId).size() - 1;
            int seriesId2Counter = data.get(seriesId2).size() - 1;
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(index < 20) {
                    //Should be first id
                    Assert.assertEquals((int)seriesId, value.getId());
                }else {
                    Assert.assertEquals((int)seriesId2, value.getId());
                }
                if(value.getId() == seriesId2) {
                    if(value.getTime() > timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    count2.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter--;
                }else {
                    if(value.getTime() > timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    count1.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter--;
                }
            }
            
        });
        Assert.assertEquals(new Integer(20), count1.getValue());
        Assert.assertEquals(new Integer(20), count2.getValue());
    }

    /* Values Between Tests */
    public void testBetweenExceptionInCallback() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp  = new MutableLong(startTs);
        this.dao.getPointValuesBetween(ids, startTs, endTs, false, null, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = 0;
            int seriesId2Counter = 3; //Skip first 3
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
                if(count.getValue() == 20)
                    throw new IOException("Exception Test");
            }
            
        });
        Assert.assertEquals(new Integer(20) , count.getValue());
    }
    
    public void testBetweenNoDataInBothSeries() {
        MutableInt count = new MutableInt();
        this.dao.getPointValuesBetween(ids, 0, series2StartTs - 1, false, null, new PVTQueryCallback<IdPointValueTime>() {
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                count.increment();
            }
            
        });
        Assert.assertEquals(new Integer(0), count.getValue());
    }

    public void testBetweenNoDataInOneSeries() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(0);
        this.dao.getPointValuesBetween(ids, 0, startTs, false, null, new PVTQueryCallback<IdPointValueTime>() {
            int seriesId2Counter = 0;
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                count.increment();
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    Assert.fail("Should not get data for series 1");
                }
            }
            
        });
        //Total is all samples + the extra 3 at the beginning of series2
        Assert.assertEquals(new Integer(3) , count.getValue());
    }
    public void testRangeMultiplePointValuesNoLimit() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(startTs);
        this.dao.getPointValuesBetween(ids, startTs, endTs, false, null, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = 0;
            int seriesId2Counter = 3; //Skip first 3
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
        });
        Assert.assertEquals(new Integer(totalSampleCount * 2) , count.getValue());
    }
    
    public void testRangeMultiplePointValuesNoLimitOffsetSeries() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(series2StartTs);
        this.dao.getPointValuesBetween(ids, series2StartTs, series2EndTs + 1, false, null, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = 0;
            int seriesId2Counter = 0;
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
        });
        Assert.assertEquals(new Integer(totalSampleCount * 2 + 6) , count.getValue());
    }
    
    public void testRangeMultiplePointValuesOrderByIdNoLimit() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp1 = new MutableLong(startTs);
        MutableLong timestamp2 = new MutableLong(startTs);
        this.dao.getPointValuesBetween(ids, startTs, endTs, true, null, new PVTQueryCallback<IdPointValueTime>() {

            int seriesIdCounter = 0;
            int seriesId2Counter = 3; //Skip first 3
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(index < data.get(seriesId).size()) {
                    //Should be first id
                    Assert.assertEquals((int)seriesId, value.getId());
                }else {
                    Assert.assertEquals((int)seriesId2, value.getId());
                }
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
        });
        Assert.assertEquals(new Integer(totalSampleCount * 2) , count.getValue());
    }
    
    public void testRangeMultiplePointValuesOrderByIdNoLimitOffsetSeries() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp1 = new MutableLong(series2StartTs);
        MutableLong timestamp2 = new MutableLong(series2StartTs);
        this.dao.getPointValuesBetween(ids, series2StartTs, series2EndTs+1, true, null, new PVTQueryCallback<IdPointValueTime>() {

            int seriesIdCounter = 0;
            int seriesId2Counter = 0; //Skip first 3
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(index < data.get(seriesId).size()) {
                    //Should be first id
                    Assert.assertEquals((int)seriesId, value.getId());
                }else {
                    Assert.assertEquals((int)seriesId2, value.getId());
                }
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
        });
        Assert.assertEquals(new Integer(totalSampleCount * 2 + 6) , count.getValue());
    }
    
    public void testRangeMultiplePointValuesLimit() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(startTs);
        this.dao.getPointValuesBetween(ids, startTs, endTs, false, 20, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = 0;
            int seriesId2Counter = 3; //Skip first 3
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
        });
        Assert.assertEquals(new Integer(20) , count.getValue());
    }
    
    public void testRangeMultiplePointValuesLimitOffsetSeries() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(series2StartTs);
        this.dao.getPointValuesBetween(ids, series2StartTs, series2EndTs, false, 20, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = 0;
            int seriesId2Counter = 0;
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
        });
        Assert.assertEquals(new Integer(20) , count.getValue());
    }
    
    public void testRangeMultiplePointValuesOrderByIdLimit() {
        MutableInt count1 = new MutableInt();
        MutableInt count2 = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp1 = new MutableLong(startTs);
        MutableLong timestamp2 = new MutableLong(startTs);
        
        this.dao.getPointValuesBetween(ids, startTs, endTs, true, 20, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = 0;
            int seriesId2Counter = 3; //Skip first 3
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(index < 20) {
                    //Should be first id
                    Assert.assertEquals((int)seriesId, value.getId());
                }else {
                    Assert.assertEquals((int)seriesId2, value.getId());
                }
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    count2.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    count1.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
        });
        Assert.assertEquals(new Integer(20), count1.getValue());
        Assert.assertEquals(new Integer(20), count2.getValue());
    }
    public void testRangeMultiplePointValuesOrderByIdLimitOffsetSeries() {
        MutableInt count1 = new MutableInt();
        MutableInt count2 = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp1 = new MutableLong(series2StartTs);
        MutableLong timestamp2 = new MutableLong(series2StartTs);
        this.dao.getPointValuesBetween(ids, series2StartTs, series2EndTs, true, 20, new PVTQueryCallback<IdPointValueTime>() {
            
            int seriesIdCounter = 0;
            int seriesId2Counter = 0; //Skip first 3
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(index < 20) {
                    //Should be first id
                    Assert.assertEquals((int)seriesId, value.getId());
                }else {
                    Assert.assertEquals((int)seriesId2, value.getId());
                }
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    count2.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    count1.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
        });
        Assert.assertEquals(new Integer(20), count1.getValue());
        Assert.assertEquals(new Integer(20), count2.getValue());
    }
 
    /* Bookend Tests */
    public void testBookendExceptionInFirstValueCallback() {
        this.dao.wideBookendQuery(ids, startTs - 1, endTs, false, null, new BookendQueryCallback<IdPointValueTime>() {
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                throw new IOException("First Value Callback Exception");
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.fail("Query cancelled, should not get any rows");
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.fail("Query cancelled, should not get last value");
            }
            
        }); 
    }
    
    public void testBookendExceptionInRowCallback() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(startTs - 1);
        this.dao.wideBookendQuery(ids, startTs - 1, endTs, false, null, new BookendQueryCallback<IdPointValueTime>() {

            int seriesIdCounter = 0;
            int seriesId2Counter = 3; //Skip first 3
            
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(2).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(startTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    //Check value is null as no data exists before the startTs for series 1
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(startTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
                if(count.getValue() == 20)
                    throw new IOException("Exception Test");
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.fail("Query cancelled, should not get last value");
            }
            
        });
        Assert.assertEquals(new Integer(20) , count.getValue());   
    }
    
    public void testBookendExceptionInLastValueCallback() {
        MutableInt count = new MutableInt();
        MutableInt lastValueCallCount = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(startTs - 1);
        this.dao.wideBookendQuery(ids, startTs - 1, endTs, false, null, new BookendQueryCallback<IdPointValueTime>() {

            int seriesIdCounter = 0;
            int seriesId2Counter = 3; //Skip first 3
            
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(2).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(startTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    //Check value is null as no data exists before the startTs for series 1
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(startTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                lastValueCallCount.increment();
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                throw new IOException("Last Value Callback Exception");
            }
            
        });
        //Since the exception is thrown in last value all the true values should have been sent out already
        Assert.assertEquals(new Integer(totalSampleCount * 2) , count.getValue());   
        //Ensure that last value is only called once due to the exception
        Assert.assertEquals(new Integer(1) , lastValueCallCount.getValue());
    }
    
    public void testBookendNoDataInBothSeries() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(0);
        this.dao.wideBookendQuery(ids, 0, series2StartTs - 1, false, null, new BookendQueryCallback<IdPointValueTime>() {
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value is null as no data exists before time 0
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(0, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    //Check value is null as no data exists before time 0
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(0, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.fail("Should not get any data");
            }
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value is null as no data exists before time 0
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(series2StartTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    //Check value is null as no data exists before time 0
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(series2StartTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
        });
        
        Assert.assertEquals(4, count.intValue());
    }

    public void testBookendNoDataInOneSeries() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(0);
        this.dao.wideBookendQuery(ids, 0, startTs, false, null, new BookendQueryCallback<IdPointValueTime>() {
            int seriesId2Counter = 0;
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value is null as no data exists before time 0
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(0, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    //Check value is null as no data exists before time 0
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(0, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    Assert.fail("Should not get data for series 1");
                }
            }
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(2).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(startTs, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    //Check value
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(startTs, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
        });
        //Total is all samples + the extra 3 at the beginning of series2
        Assert.assertEquals(new Integer(7) , count.getValue());
    }
    
    public void testBookendMultiplePointValuesNoLimit() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(startTs - 1);
        this.dao.wideBookendQuery(ids, startTs - 1, endTs, false, null, new BookendQueryCallback<IdPointValueTime>() {

            int seriesIdCounter = 0;
            int seriesId2Counter = 3; //Skip first 3
            
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(2).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(startTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    //Check value is null as there is no value before startTs
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(startTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //This has a value before the startTs
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(totalSampleCount + 2).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(endTs, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(totalSampleCount - 1).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(endTs, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
        });
        Assert.assertEquals(new Integer(totalSampleCount * 2) , count.getValue());   
    }
    
    public void testBookendMultiplePointValuesNoLimitOffsetSeries() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(series2StartTs - 1);
        this.dao.wideBookendQuery(ids, series2StartTs - 1, series2EndTs + 1, false, null, new BookendQueryCallback<IdPointValueTime>() {

            int seriesIdCounter = 0;
            int seriesId2Counter = 0;
            
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value is null as no data exists before the start time for series 2
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(series2StartTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    //Check value is null as no data exists before the startTs for series 1
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(series2StartTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(data.get(value.getId()).size() - 1).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(series2EndTs + 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(totalSampleCount - 1).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(series2EndTs + 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
        });
        Assert.assertEquals(new Integer(totalSampleCount * 2 + 6) , count.getValue());   
    }
    
    public void testBookendMultiplePointValuesOrderByIdNoLimit() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp1 = new MutableLong(startTs - 1);
        MutableLong timestamp2 = new MutableLong(startTs - 1);
        this.dao.wideBookendQuery(ids, startTs - 1, endTs, true, null, new BookendQueryCallback<IdPointValueTime>() {

            int seriesIdCounter = 0;
            int seriesId2Counter = 3; //Skip first 3
            
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //This has a value before the startTs
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(2).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(startTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Check value is null as no data exists before the startTs for series 1
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(startTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(index < data.get(seriesId).size() + 1) { //1 for end bookend
                    //Should be first id
                    Assert.assertEquals((int)seriesId, value.getId());
                }else {
                    Assert.assertEquals((int)seriesId2, value.getId());
                }
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //This has a value before the startTs
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(totalSampleCount + 2).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(endTs, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(totalSampleCount - 1).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(endTs, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
        });
        Assert.assertEquals(new Integer(totalSampleCount * 2) , count.getValue()); 
    }
    
    public void testBookendMultiplePointValuesOrderByIdNoLimitOffsetSeries() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp1 = new MutableLong(series2StartTs - 1);
        MutableLong timestamp2 = new MutableLong(series2StartTs - 1);
        this.dao.wideBookendQuery(ids, series2StartTs - 1, series2EndTs + 1, true, null, new BookendQueryCallback<IdPointValueTime>() {

            int seriesIdCounter = 0;
            int seriesId2Counter = 0;
            
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //Check value is null as no data exists before the start Ts for series 2
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(series2StartTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Check value is null as no data exists before the startTs for series 1
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(series2StartTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(index < data.get(seriesId).size() + 1) {
                    //Should be first id
                    Assert.assertEquals((int)seriesId, value.getId());
                }else {
                    Assert.assertEquals((int)seriesId2, value.getId());
                }
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //This has a value before the startTs
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(data.get(value.getId()).size() - 1).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(series2EndTs + 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(totalSampleCount - 1).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(series2EndTs + 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
        });
        Assert.assertEquals(new Integer(totalSampleCount * 2 + 6) , count.getValue()); 
    }
    
    public void testBookendMultiplePointValuesLimit() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(startTs - 1);
        this.dao.wideBookendQuery(ids, startTs - 1, endTs, false, 20, new BookendQueryCallback<IdPointValueTime>() {

            int seriesIdCounter = 0;
            int seriesId2Counter = 3; //Skip first 3
            
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //This has a value before the startTs
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(2).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(startTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    //Check value is null as no data exists before the startTs for series 1
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(startTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Limited queries bookend
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(--seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(endTs, value.getTime());
                }else {
                    //Limited Query Bookend
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(--seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(endTs, value.getTime());
                    //Limited queries do not have bookends
                }
            }
            
        });
        Assert.assertEquals(new Integer(20) , count.getValue()); 
    }
    
    public void testBookendMultiplePointValuesLimitOffsetSeries() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(series2StartTs - 1);
        this.dao.wideBookendQuery(ids, series2StartTs - 1, series2EndTs + 1, false, 20, new BookendQueryCallback<IdPointValueTime>() {

            int seriesIdCounter = 0;
            int seriesId2Counter = 0;
            
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value is null as no data exists before the start Ts for series 2
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(series2StartTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    //Check value is null as no data exists before the startTs for series 1
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(series2StartTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                count.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //This has a value before the startTs
                    //Limited query bookend
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(--seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(series2EndTs + 1, value.getTime());
                }else {
                    //Ensure bookend
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(--seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(series2EndTs + 1, value.getTime());
                    //Limited queries do not have bookends
                }
            }
            
        });
        Assert.assertEquals(new Integer(20) , count.getValue()); 
    }
    
    public void testBookendMultiplePointValuesOrderByIdLimit() {
        MutableInt count1 = new MutableInt();
        MutableInt count2 = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp1 = new MutableLong(startTs - 1);
        MutableLong timestamp2 = new MutableLong(startTs - 1);
        this.dao.wideBookendQuery(ids, startTs - 1, endTs, true, 20, new BookendQueryCallback<IdPointValueTime>() {

            int seriesIdCounter = 0;
            int seriesId2Counter = 3; //Skip first 3
            
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //This has a value before the startTs
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(2).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(startTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Check value is null as no data exists before the startTs for series 1
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(startTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(index < 20 + 1) {
                    //Should be first id
                    Assert.assertEquals((int)seriesId, value.getId());
                }else {
                    Assert.assertEquals((int)seriesId2, value.getId());
                }
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    count2.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    count1.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //This has a value before the startTs
                    //Check bookend
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(--seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(endTs, value.getTime());
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Limited queries bookend
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(--seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(endTs, value.getTime());
                }
            }
            
        });
        Assert.assertEquals(new Integer(20), count1.getValue());
        Assert.assertEquals(new Integer(20), count2.getValue());
    }
    
    public void testBookendMultiplePointValuesOrderByIdLimitOffsetSeries() {
        MutableInt count1 = new MutableInt();
        MutableInt count2 = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp1 = new MutableLong(series2StartTs - 1);
        MutableLong timestamp2 = new MutableLong(series2StartTs - 1);
        this.dao.wideBookendQuery(ids, series2StartTs - 1, series2EndTs + 1, true, 20, new BookendQueryCallback<IdPointValueTime>() {

            int seriesIdCounter = 0;
            int seriesId2Counter = 0;
            
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //Check value is null as no data exists before the start Ts for series 2
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(series2StartTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Check value is null as no data exists before the startTs for series 1
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(series2StartTs - 1, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(index < 20 + 1) {
                    //Should be first id
                    Assert.assertEquals((int)seriesId, value.getId());
                }else {
                    Assert.assertEquals((int)seriesId2, value.getId());
                }
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    count2.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    count1.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //This has a value before the startTs
                    //Limited but has bookend
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(--seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(series2EndTs + 1, value.getTime());
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Limited but has bookend
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(--seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(series2EndTs + 1, value.getTime());
                }
            }
            
        });
        Assert.assertEquals(new Integer(20), count1.getValue());
        Assert.assertEquals(new Integer(20), count2.getValue());
    }
    
    /**
     * Query with only 1 value at the start time for series 2
     */
    public void testSeries1NoDataSeries2OneSampleOrderById() {
        MutableInt count1 = new MutableInt();
        MutableInt count2 = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp1 = new MutableLong(series2StartTs);
        MutableLong timestamp2 = new MutableLong(series2StartTs);
        
        this.dao.wideBookendQuery(ids, series2StartTs, series2StartTs + 2, true, 20, new BookendQueryCallback<IdPointValueTime>() {
            
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //Check value is null as no data exists before the start Ts for series 2
                    Assert.assertEquals(data.get(seriesId2).get(0).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(series2StartTs, value.getTime());
                    Assert.assertEquals(false, bookend);
                    count1.increment();
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Check value is null as no data exists before the startTs for series 1
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(series2StartTs, value.getTime());
                    Assert.assertEquals(true, bookend);
                    count2.increment();
                }
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.fail("No data in query period, should not call row");
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //This has a value before the startTs
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(0).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(series2StartTs + 2, value.getTime());
                    count1.increment();
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(series2StartTs + 2, value.getTime());
                    count2.increment();
                }
            }
            
        });
        Assert.assertEquals(new Integer(2), count1.getValue());
        Assert.assertEquals(new Integer(2), count2.getValue());
    }
    
    public void testNoStartBookendOrderByIdLimit() {
        MutableInt count1 = new MutableInt();
        MutableInt count2 = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp1 = new MutableLong(startTs);
        MutableLong timestamp2 = new MutableLong(startTs);
        this.dao.wideBookendQuery(ids, startTs, endTs, true, 20, new BookendQueryCallback<IdPointValueTime>() {

            int seriesIdCounter = 0;
            int seriesId2Counter = 3;
            
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    Assert.assertEquals(data.get(seriesId2).get(seriesId2Counter++).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(startTs, value.getTime());
                    Assert.assertEquals(false, bookend);
                    count1.increment();
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    //Check value is null as no data exists before the startTs for series 1
                    Assert.assertEquals(data.get(seriesId).get(seriesIdCounter++).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(startTs, value.getTime());
                    Assert.assertEquals(false, bookend);
                    count2.increment();
                }
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(index < 20 + 1) {
                    //Should be first id
                    Assert.assertEquals((int)seriesId, value.getId());
                }else {
                    Assert.assertEquals((int)seriesId2, value.getId());
                }
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    count2.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    count1.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getId() == seriesId2) {
                    if(value.getTime() < timestamp2.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp2.setValue(value.getTime());
                    //This has a value before the startTs
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(--seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(endTs, value.getTime());
                    count1.increment();
                }else {
                    if(value.getTime() < timestamp1.getValue())
                        Assert.fail("Timestamp out of order.");
                    timestamp1.setValue(value.getTime());
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(--seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(endTs, value.getTime());
                    count2.increment();
                }
            }
            
        });
        Assert.assertEquals(new Integer(21), count1.getValue());
        Assert.assertEquals(new Integer(21), count2.getValue());
    }
    
    /**
     * Query with only 1 value at the start time for series 2
     */
    public void testSeries1NoDataSeries2OneSample() {
        MutableInt count = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(series2StartTs);
        
        this.dao.wideBookendQuery(ids, series2StartTs, series2StartTs + 2, false, 20, new BookendQueryCallback<IdPointValueTime>() {
            
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                count.increment();
                if(value.getId() == seriesId2) {
                    //Check value is null as no data exists before the start Ts for series 2
                    Assert.assertEquals(data.get(seriesId2).get(0).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(series2StartTs, value.getTime());
                    Assert.assertEquals(false, bookend);

                }else {
                    //Check value is null as no data exists before the startTs for series 1
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(series2StartTs, value.getTime());
                    Assert.assertEquals(true, bookend);
                }
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.fail("No data in query period, should not call row");
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                count.increment();
                if(value.getId() == seriesId2) {
                    //This has a value before the startTs
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(0).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(series2StartTs + 2, value.getTime());
                }else {
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertNull(value.getValue());
                    //Check time
                    Assert.assertEquals(series2StartTs + 2, value.getTime());
                }
            }
            
        });
        Assert.assertEquals(new Integer(4), count.getValue());
    }
    
    public void testNoStartBookendLimit() {
        MutableInt count1 = new MutableInt();
        MutableInt count2 = new MutableInt();
        MutableInt mutableIndex = new MutableInt();
        MutableLong timestamp = new MutableLong(startTs);
        this.dao.wideBookendQuery(ids, startTs, endTs, false, 20, new BookendQueryCallback<IdPointValueTime>() {

            int seriesIdCounter = 0;
            int seriesId2Counter = 3;
            
            @Override
            public void firstValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    Assert.assertEquals(data.get(seriesId2).get(seriesId2Counter++).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(startTs, value.getTime());
                    Assert.assertEquals(false, bookend);
                    count1.increment();
                }else {
                    //Check value is null as no data exists before the startTs for series 1
                    Assert.assertEquals(data.get(seriesId).get(seriesIdCounter++).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(startTs, value.getTime());
                    Assert.assertEquals(false, bookend);
                    count2.increment();
                }
            }
            
            @Override
            public void row(IdPointValueTime value, int index) throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    count2.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesId2Counter).getTime(), value.getTime());
                    seriesId2Counter++;
                }else {
                    count1.increment();
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(data.get(value.getId()).get(seriesIdCounter).getTime(), value.getTime());
                    seriesIdCounter++;
                }
            }
            
            @Override
            public void lastValue(IdPointValueTime value, int index, boolean bookend)
                    throws IOException {
                Assert.assertEquals(mutableIndex.intValue(), index);
                mutableIndex.increment();
                if(value.getTime() < timestamp.getValue())
                    Assert.fail("Timestamp out of order.");
                timestamp.setValue(value.getTime());
                if(value.getId() == seriesId2) {
                    //This has a value before the startTs
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(--seriesId2Counter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(endTs, value.getTime());
                    count1.increment();
                }else {
                    Assert.assertEquals(true, bookend);
                    //Check value
                    Assert.assertEquals(data.get(value.getId()).get(--seriesIdCounter).getDoubleValue(), value.getDoubleValue(), 0.001);
                    //Check time
                    Assert.assertEquals(endTs, value.getTime());
                    count2.increment();
                }
            }
            
        });
        Assert.assertEquals(new Integer(11), count1.getValue());
        Assert.assertEquals(new Integer(11), count2.getValue());
    }
}
