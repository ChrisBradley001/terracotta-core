/*
 * Copyright (c) 2003-2006 Terracotta, Inc. All rights reserved.
 */
package com.tc.stats.statistics;

public class CountStatisticImpl extends StatisticImpl implements CountStatistic {
  private long m_count;

  public CountStatisticImpl() {
    this(0L);
  }

  public CountStatisticImpl(long count) {
    m_count = count;
  }

  public void setCount(long count) {
    m_count = count;
  }

  public long getCount() {
    return m_count;
  }
}
