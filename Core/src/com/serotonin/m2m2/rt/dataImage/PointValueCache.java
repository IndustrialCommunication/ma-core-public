/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */
package com.serotonin.m2m2.rt.dataImage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.infiniteautomation.mango.pointvalue.PointValueCacheDao;
import com.serotonin.m2m2.db.dao.PointValueDao;
import com.serotonin.m2m2.vo.DataPointVO;

/**
 * This class maintains an ordered list of the most recent values for a data point. It will mirror values in the
 * database, but provide a much faster lookup for a limited number of values.
 *
 * Because there is not a significant performance problem for time-based lookups, they are not handled here, but rather
 * are still handled by the database.
 *
 * @author Matthew Lohbihler
 * @author Jared Wiltshire
 */
public class PointValueCache {
    private final DataPointVO vo;
    private final int defaultSize;
    private final PointValueDao dao;
    private final PointValueCacheDao pointValueCacheDao;

    private volatile List<PointValueTime> cache;

    public PointValueCache(DataPointVO vo, int defaultSize, @Nullable List<PointValueTime> initialCache, PointValueDao dao, PointValueCacheDao pointValueCacheDao) {
        this.vo = vo;
        this.defaultSize = defaultSize;
        this.dao = dao;
        this.pointValueCacheDao = pointValueCacheDao;
        this.cache = initialCache;
    }

    void savePointValueAsync(PointValueTime pvt, SetPointSource source) {
        dao.savePointValueAsync(vo, pvt, source);
    }

    PointValueTime savePointValueSync(PointValueTime pvt, SetPointSource source) {
        return dao.savePointValueSync(vo, pvt, source);
    }

    public void savePointValue(PointValueTime pvt, @Nullable SetPointSource source, boolean logValue, boolean async) {
        if (logValue) {
            if (async) {
                savePointValueAsync(pvt, source);
            } else {
                pvt = savePointValueSync(pvt, source);
            }
        }

        synchronized (this) {
            if (defaultSize == 0) {
                this.cache = Collections.emptyList();
            } else if (defaultSize == 1) {
                this.cache = Collections.singletonList(pvt);
            } else {
                var existing = cache;
                this.cache = new ArrayList<>(existing.size() + 1);
                cache.add(pvt);
                cache.addAll(existing);
                cache.sort(Collections.reverseOrder());

                // remove items from end of list until list is under the size limit
                while (cache.size() > defaultSize) {
                    cache.remove(cache.size() - 1);
                }
            }

            pointValueCacheDao.updateCache(vo, cache);
        }
    }

    /**
     * @return the latest point value, or null if none
     */
    @Nullable
    public PointValueTime getLatestPointValue() {
        List<PointValueTime> cache = getCacheContents();
        return !cache.isEmpty() ? cache.get(0) : null;
    }

    /**
     * @param limit max number of point values to return
     * @return latest point values, from cache if possible (unmodifiable)
     */
    public List<PointValueTime> getLatestPointValues(int limit) {
        List<PointValueTime> cache = getCacheContents();
        if (cache.size() >= limit) {
            return Collections.unmodifiableList(cache.subList(0, limit));
        }
        return Collections.unmodifiableList(dao.getLatestPointValues(vo, limit));
    }

    /**
     * @return unmodifiable list of cache contents, causes cache load if not already loaded.
     */
    public List<PointValueTime> getCacheContents() {
        var cache = this.cache;
        if (cache == null) {
            synchronized (this) {
                cache = this.cache;
                if (cache == null) {
                    this.cache = cache = pointValueCacheDao.loadCache(vo, defaultSize);
                }
            }
        }
        return Collections.unmodifiableList(cache);
    }

    /**
     * Invalidate the cache, so it will be reloaded on next access.
     */
    public void invalidate(boolean invalidatePersisted) {
        synchronized (this) {
            this.cache = null;
            if (invalidatePersisted) {
                pointValueCacheDao.deleteCache(vo);
            }
        }
    }

}
