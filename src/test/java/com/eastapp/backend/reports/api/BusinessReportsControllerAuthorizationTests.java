package com.eastapp.backend.reports.api;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessReportsControllerAuthorizationTests {
    private static final String SALES_ACCESS =
            "hasAuthority('PERMISSION_SALES_REPORT_ACCESS')";

    @Test
    void everySalesEndpointRequiresDedicatedManagerOrAbovePermission() {
        assertSalesAccess(method(
                "salesHistory",
                AuthenticatedUser.class,
                LocalDate.class,
                LocalDate.class,
                Integer.class
        ));
        assertSalesAccess(method("sales", AuthenticatedUser.class, LocalDate.class));
        assertSalesAccess(method("salesCashRecipients", AuthenticatedUser.class));
        assertSalesAccess(method(
                "upsertSales",
                AuthenticatedUser.class,
                UpsertSalesReportRequest.class
        ));
        assertSalesAccess(method(
                "addVoidBill",
                AuthenticatedUser.class,
                AddVoidBillRequest.class
        ));
        assertSalesAccess(method(
                "submitSalesDirect",
                AuthenticatedUser.class,
                UpsertSalesReportRequest.class
        ));
        assertSalesAccess(method("submitSales", AuthenticatedUser.class, UUID.class));
    }

    private Method method(String name, Class<?>... parameterTypes) {
        try {
            return BusinessReportsController.class.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    private void assertSalesAccess(Method method) {
        assertEquals(SALES_ACCESS, method.getAnnotation(PreAuthorize.class).value());
    }
}
