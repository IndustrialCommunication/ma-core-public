/**
 * Copyright (C) 2015 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.util;

import static org.junit.Assert.assertEquals;

import java.util.Date;

import org.joda.time.DateTime;
import org.junit.Test;

import com.serotonin.m2m2.Common.TimePeriods;

/**
 * @author Terry Packer
 *
 */
public class DateUtilsTest {

	@Test
	public void quickTest(){
		DateTime baseTime = DateTime.parse("2013-02-10T10:34:23.230-07:00");
		
		int periodType = TimePeriods.MONTHS;
		int count = 2;
		
		long to = DateUtils.truncate(baseTime.getMillis(), periodType);
		long from = DateUtils.minus(to, periodType, count);
		
		DateTime startOfMonth = baseTime.minusMonths(count).withDayOfMonth(1).withTimeAtStartOfDay();
		assertEquals(startOfMonth.getMillis(), from);

		DateTime endOfMonth = startOfMonth.plusMonths(count);
		assertEquals(endOfMonth.getMillis(), to);

	}
	@Test
	public void testPreviousMinutes(){
		int count = 62;
		while(count > 0){
			DateTime baseTime = new DateTime().minusMonths(1);
			long now = new Date().getTime();
			while(baseTime.isBefore(now)){
			
				int periodType = TimePeriods.MINUTES;

				
				long to = DateUtils.truncate(baseTime.getMillis(), periodType);
				long from = DateUtils.minus(to, periodType, count);
				
				DateTime start = baseTime.minusMinutes(count).minuteOfHour().roundFloorCopy();
				DateTime end = start.plusMinutes(count);
				
//				Double days = (double)(end.getMillis() - start.getMillis())/(1000D*60D*60D*24D);
//				System.out.println("Period: " + start.toString("MMM-yyyy") + " - " + end.toString("MMM-yyyy") + " Duration in Days: " + days);
				
				assertEquals(start.getMillis(), from);
		
				assertEquals(end.getMillis(), to);
				
				
				//Step along
				baseTime = baseTime.plusMinutes(1);
			}
			count--;
		}
	}
	@Test
	public void testPreviousHours(){
		int count = 364;
		while(count > 0){
			DateTime baseTime = new DateTime().minusYears(2);
			long now = new Date().getTime();
			while(baseTime.isBefore(now)){
			
				int periodType = TimePeriods.HOURS;

				
				long to = DateUtils.truncate(baseTime.getMillis(), periodType);
				long from = DateUtils.minus(to, periodType, count);
				
				DateTime start = baseTime.minusHours(count).hourOfDay().roundFloorCopy();
				DateTime end = start.plusHours(count);
				
//				Double days = (double)(end.getMillis() - start.getMillis())/(1000D*60D*60D*24D);
//				System.out.println("Period: " + start.toString("MMM-yyyy") + " - " + end.toString("MMM-yyyy") + " Duration in Days: " + days);
				
				assertEquals(start.getMillis(), from);
		
				assertEquals(end.getMillis(), to);
				
				
				//Step along
				baseTime = baseTime.plusHours(1);
			}
			count--;
		}
	}
	@Test
	public void testPreviousDays(){
		int count = 780;
		while(count > 0){
			DateTime baseTime = new DateTime().minusYears(5);
			long now = new Date().getTime();
			while(baseTime.isBefore(now)){
			
				int periodType = TimePeriods.DAYS;

				
				long to = DateUtils.truncate(baseTime.getMillis(), periodType);
				long from = DateUtils.minus(to, periodType, count);
				
				DateTime start = baseTime.minusDays(count).withTimeAtStartOfDay();
				DateTime end = start.plusDays(count);
				
//				Double days = (double)(end.getMillis() - start.getMillis())/(1000D*60D*60D*24D);
//				System.out.println("Period: " + start.toString("MMM-yyyy") + " - " + end.toString("MMM-yyyy") + " Duration in Days: " + days);
				
				assertEquals(start.getMillis(), from);
		
				assertEquals(end.getMillis(), to);
				
				
				//Step along
				baseTime = baseTime.plusDays(1);
			}
			count--;
		}
	}
	@Test
	public void testPreviousWeeks(){
		int count = 200;
		while(count > 0){
			DateTime baseTime = new DateTime().minusYears(5);
			long now = new Date().getTime();
			while(baseTime.isBefore(now)){
			
				int periodType = TimePeriods.WEEKS;

				
				long to = DateUtils.truncate(baseTime.getMillis(), periodType);
				long from = DateUtils.minus(to, periodType, count);
				
				DateTime start = baseTime.minusWeeks(count).withDayOfWeek(1).withTimeAtStartOfDay();
				DateTime end = start.plusWeeks(count);
				
//				Double days = (double)(end.getMillis() - start.getMillis())/(1000D*60D*60D*24D);
//				System.out.println("Period: " + start.toString("MMM-yyyy") + " - " + end.toString("MMM-yyyy") + " Duration in Days: " + days);
				
				assertEquals(start.getMillis(), from);
		
				assertEquals(end.getMillis(), to);
				
				
				//Step along
				baseTime = baseTime.plusWeeks(1);
			}
			count--;
		}
	}
	
	@Test
	public void testPreviousMonths(){
		int count = 24;
		while(count > 0){
			DateTime baseTime = new DateTime().minusYears(5);
			long now = new Date().getTime();
			while(baseTime.isBefore(now)){
			
				int periodType = TimePeriods.MONTHS;

				
				long to = DateUtils.truncate(baseTime.getMillis(), periodType);
				long from = DateUtils.minus(to, periodType, count);
				
				DateTime start = baseTime.minusMonths(count).withDayOfMonth(1).withTimeAtStartOfDay();
				DateTime end = start.plusMonths(count);
				
//				Double days = (double)(end.getMillis() - start.getMillis())/(1000D*60D*60D*24D);
//				System.out.println("Period: " + start.toString("MMM-yyyy") + " - " + end.toString("MMM-yyyy") + " Duration in Days: " + days);
				
				assertEquals(start.getMillis(), from);
		
				assertEquals(end.getMillis(), to);
				
				
				//Step along
				baseTime = baseTime.plusMonths(1);
			}
			count--;
		}
	}
	@Test
	public void testPreviousYears(){
		int count = 24;
		while(count > 0){
			DateTime baseTime = new DateTime().minusYears(10);
			long now = new Date().getTime();
			while(baseTime.isBefore(now)){
			
				int periodType = TimePeriods.YEARS;

				
				long to = DateUtils.truncate(baseTime.getMillis(), periodType);
				long from = DateUtils.minus(to, periodType, count);
				
				DateTime start = baseTime.minusYears(count).withDayOfYear(1).withTimeAtStartOfDay();
				DateTime end = start.plusYears(count);
				
//				Double days = (double)(end.getMillis() - start.getMillis())/(1000D*60D*60D*24D);
//				System.out.println("Period: " + start.toString("MMM-yyyy") + " - " + end.toString("MMM-yyyy") + " Duration in Days: " + days);
				
				assertEquals(start.getMillis(), from);
		
				assertEquals(end.getMillis(), to);
				
				
				//Step along
				baseTime = baseTime.plusYears(1);
			}
			count--;
		}
	}
}
