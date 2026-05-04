package com.web.service;

import java.util.Map;

public interface AdminPaymentOverviewService {
    Map<String, Object> getOverview(int limit);
}
