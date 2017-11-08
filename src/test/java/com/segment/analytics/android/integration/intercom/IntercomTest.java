package com.segment.analytics.android.integration.intercom;

import android.app.Application;
import com.segment.analytics.Options;
import com.segment.analytics.Properties;
import com.segment.analytics.Properties.Product;
import com.segment.analytics.Traits;
import com.segment.analytics.ValueMap;
import com.segment.analytics.android.integrations.intercom.IntercomIntegration;
import com.segment.analytics.core.tests.BuildConfig;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.test.GroupPayloadBuilder;
import com.segment.analytics.test.IdentifyPayloadBuilder;
import com.segment.analytics.test.TrackPayloadBuilder;
import io.intercom.android.sdk.Company;
import io.intercom.android.sdk.Intercom;
import io.intercom.android.sdk.UserAttributes;
import io.intercom.android.sdk.identity.Registration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.matcher.AssertionMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.segment.analytics.Analytics.LogLevel.VERBOSE;
import static com.segment.analytics.Utils.createTraits;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
@PowerMockIgnore({ "org.mockito.*", "org.roboelectric.*", "android.*" })
@PrepareForTest(Intercom.class)
public class IntercomTest {
    @Rule public PowerMockRule rule = new PowerMockRule();
    @Mock Application application;
    @Mock Intercom intercom;
    private IntercomIntegration integration;
    private IntercomIntegration.Provider mockProvider = new IntercomIntegration.Provider() {
        @Override
        public Intercom get() {
            return intercom;
        }
    };

    @Before
    public void setUp() {
        initMocks(this);
        integration = new IntercomIntegration(mockProvider, application, new ValueMap()
                .putValue("mobileApiKey", "123")
                .putValue("appId", "123"),
                Logger.with(VERBOSE));
    }

    @Test
    public void initialize() {
        PowerMockito.mockStatic(Intercom.class);
        integration = new IntercomIntegration(mockProvider, application,
            new ValueMap()
                .putValue("mobileApiKey", "123")
                .putValue("appId", "123"),
            Logger.with(VERBOSE));

        verifyStatic();
        Intercom.initialize(application, "123", "123");
    }

    @Test
    public void identifyWithUserId() {
        Traits traits = createTraits("123");
        integration.identify(new IdentifyPayloadBuilder()
            .traits(traits)
            .build());

        Registration expectedRegistration = Registration.create().withUserId("123");
        verify(intercom).registerIdentifiedUser(isEqualToComparingFieldByFieldRecursively(expectedRegistration));
    }

    @Test
    public void identifyWithoutUserId() {
        integration.identify(new IdentifyPayloadBuilder()
            .traits(new Traits())
            .build());
        verify(intercom).registerUnidentifiedUser();
    }

    @Test
    public void identifyWithUserHash() {
        Traits traits = createTraits("123");
        integration.identify(new IdentifyPayloadBuilder()
            .traits(traits)
            .options(new Options()
                .setIntegrationOptions("Intercom", new ValueMap()
                    .putValue("userHash", "567")
                )
            )
            .build());

        verify(intercom).setUserHash("567");
    }

    @Test
    public void identifyWithSpeccedAttributes() {
        long createdAt = 123344L;

        Traits traits = createTraits("123")
            .putName("Brennan")
            .putEmail("testing@segment.com")
            .putPhone("1112223333");

        Options options = new Options();
        Map<String, Object> intercomOptions = new HashMap<>();
        intercomOptions.put("languageOverride", "testing");
        intercomOptions.put("createdAt", createdAt);
        intercomOptions.put("unsubscribedFromEmails", true);
        options.setIntegrationOptions("Intercom", intercomOptions);

        integration.identify(new IdentifyPayloadBuilder()
            .traits(traits)
            .options(options)
            .build());

        UserAttributes expectedUserAttributes = new UserAttributes.Builder()
            .withName("Brennan")
            .withEmail("testing@segment.com")
            .withPhone("1112223333")
            .withLanguageOverride("testing")
            .withSignedUpAt(123344L)
            .withUnsubscribedFromEmails(true)
            .build();

        verify(intercom).updateUser(isEqualToComparingFieldByFieldRecursively(expectedUserAttributes));
    }

    @Test
    public void identifyWithCompany() {
        Map<String, Object> company = new HashMap<>();
        company.put("id", "456");
        company.put("name", "Acme");

        Traits traits = createTraits("123")
            .putValue("company", company);

        integration.identify(new IdentifyPayloadBuilder()
            .traits(traits)
            .build());

        Company expectedCompany = new Company.Builder()
            .withCompanyId("456")
            .withName("Acme")
            .build();

        UserAttributes expectedUserAttributes = new UserAttributes.Builder()
            .withCompany(expectedCompany)
            .build();

        verify(intercom).updateUser(isEqualToComparingFieldByFieldRecursively(expectedUserAttributes));
    }

    @Test
    public void identifyWithIllegalNestedProperties() {
        Map<String, Object> address = new HashMap<>();
        address.put("city", "San Francisco");
        address.put("state", "California");
        List<String> list = new ArrayList<>();

        Traits traits = createTraits("123");
        traits.put("address", address);
        traits.put("list", list);

        integration.identify(new IdentifyPayloadBuilder()
            .traits(traits)
            .build());

        UserAttributes expectedUserAttributes = new UserAttributes.Builder()
            .build();

        verify(intercom).updateUser(isEqualToComparingFieldByFieldRecursively(expectedUserAttributes));
    }

    @Test
    public void trackWithCustomProperties() {
        Properties properties = new Properties();
        properties.putValue("foo", "bar");

        integration.track(new TrackPayloadBuilder()
            .event("Baz")
            .properties(properties)
            .build());

        verify(intercom).logEvent("Baz", properties);
    }

    @Test
    public void trackWithIllegalNestedProperties() {
        Map<String, Object> bar = new HashMap<>();
        List<String> list = new ArrayList<>();

        Properties properties = new Properties()
            .putValue("foo", bar)
            .putValue("baz", "yo")
            .putValue("list", list);

        integration.track(new TrackPayloadBuilder()
            .event("Baz")
            .properties(properties)
            .build());

        properties.remove("foo");
        properties.remove("list");

        verify(intercom).logEvent("Baz", properties);
    }

    @Test
    public void trackWithRevenue() {
        Product product = new Product("456", "ABC", 100.0);
        Product product2 = new Product("789", "DEF", 100.0);

        Properties properties = new Properties()
            .putValue("orderId", "12345")
            .putValue("revenue", 100.0)
            .putCurrency("USD")
            .putProducts(product, product2);

        integration.track(new TrackPayloadBuilder()
            .event("Order Completed")
            .properties(properties)
            .build());

        Map<String, Object> price = new HashMap<>();
        price.put("amount", 10000);
        price.put("currency", "USD");

        Map<String, Object> expectedProperties = new HashMap<>();
        expectedProperties.put("orderId", "12345");
        expectedProperties.put("price", price);

        verify(intercom).logEvent("Order Completed", expectedProperties);
    }

    @Test
    public void groupWithSpeccedAttributes() {
        long createdAt = 123344L;
        int monthlySpend = 100;

        Traits traits = new Traits();
        traits.put("name", "Acme");
        traits.put("createdAt", createdAt);
        traits.put("monthlySpend", monthlySpend);
        traits.put("plan", "startup");

        integration.group(new GroupPayloadBuilder()
            .groupId("123")
            .groupTraits(traits)
            .build());

        Company expectedCompany = new Company.Builder()
            .withCompanyId("123")
            .withName("Acme")
            .withCreatedAt(createdAt)
            .withMonthlySpend(monthlySpend)
            .withPlan("startup")
            .build();

        UserAttributes expectedUserAttributes = new UserAttributes.Builder()
            .withCompany(expectedCompany)
            .build();

        verify(intercom).updateUser(isEqualToComparingFieldByFieldRecursively(expectedUserAttributes));
    }

    @Test
    public void groupWithIllegalNestedTraits() {
        Map<String, Object> address = new HashMap<>();
        address.put("city", "San Francisco");
        address.put("state", "California");
        List<String> list = new ArrayList<>();

        Traits traits = new Traits();
        traits.put("address", address);
        traits.put("list", list);

        integration.group(new GroupPayloadBuilder()
            .groupId("123")
            .build());

        Company expectedCompany = new Company.Builder()
            .withCompanyId("123")
            .build();

        UserAttributes expectedUserAttributes = new UserAttributes.Builder()
            .withCompany(expectedCompany)
            .build();

        verify(intercom).updateUser(isEqualToComparingFieldByFieldRecursively(expectedUserAttributes));
    }

    @Test
    public void reset() {
        integration.reset();
        verify(intercom).logout();
    }

    private static <T> T isEqualToComparingFieldByFieldRecursively(final T expected) {
        return argThat(new AssertionMatcher<T>(){
            @Override
            public void assertion(T actual) throws AssertionError {
                assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
            }
        });
    }
}