package com.segment.analytics.android.integration.intercom;

import android.app.Application;

import com.segment.analytics.Analytics;
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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

import io.intercom.android.sdk.Intercom;
import io.intercom.android.sdk.UserAttributes;
import io.intercom.android.sdk.identity.Registration;

import static com.segment.analytics.Analytics.LogLevel.VERBOSE;
import static com.segment.analytics.Utils.createTraits;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
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
    @Mock Analytics analytics;
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
        integration = new IntercomIntegration(mockProvider, application, new ValueMap()
                .putValue("mobileApiKey", "123")
                .putValue("appId", "123"),
                Logger.with(VERBOSE));

        verifyStatic();
        Intercom.initialize(application, "123", "123");
    }

    @Test
    public void identify() {
        Traits traits = createTraits("123");
        integration.identify(new IdentifyPayloadBuilder().traits(traits).build());

        ArgumentCaptor<Registration> argument = ArgumentCaptor.forClass(Registration.class);
        verify(intercom).registerIdentifiedUser(argument.capture());
        assertEquals("123", argument.getValue().getUserId());
    }

    @Test
    public void identifyWithUserHash() {
        Traits traits = createTraits("123");
        Options options = new Options();
        Map<String, Object> intercomOptions = new HashMap<>();
        intercomOptions.put("userHash", "567");
        options.setIntegrationOptions("Intercom", intercomOptions);
        integration.identify(new IdentifyPayloadBuilder().traits(traits).options(options).build());
        verify(intercom).setUserHash("567");
    }

    @Test
    public void identifyWithSpeccedAttributes() {
        long createdAt = 123344L;
        Traits traits = createTraits("123");
        traits.putName("Brennan");
        traits.putEmail("testing@segment.com");
        traits.putPhone("1112223333");
        Options options = new Options();
        Map<String, Object> intercomOptions = new HashMap<>();
        intercomOptions.put("languageOverride", "testing");
        intercomOptions.put("createdAt", createdAt);
        intercomOptions.put("unsubscribedFromEmails", true);
        options.setIntegrationOptions("Intercom", intercomOptions);
        integration.identify(new IdentifyPayloadBuilder().traits(traits).options(options).build());

        ArgumentCaptor<UserAttributes> attributesArgumentCaptor = ArgumentCaptor.forClass(UserAttributes.class);
        verify(intercom).updateUser(attributesArgumentCaptor.capture());

        UserAttributes capturedAttributes = attributesArgumentCaptor.getValue();
        Map<String, Object> mapOfAttributes = capturedAttributes.toMap();

        assertEquals("Brennan", String.valueOf(mapOfAttributes.get("name")));
        assertEquals("testing@segment.com", String.valueOf(mapOfAttributes.get("email")));
        assertEquals("1112223333", String.valueOf(mapOfAttributes.get("phone")));
        assertEquals("testing", String.valueOf(mapOfAttributes.get("language_override")));
        assertEquals(createdAt, mapOfAttributes.get("signed_up_at"));
        assertEquals(true, mapOfAttributes.get("unsubscribed_from_emails"));
    }

    @Test
    public void identifyWithCompany() {
        Traits traits = createTraits("123");
        Map<String, Object> company = new HashMap<>();
        company.put("id", "456");
        company.put("name", "Acme");
        traits.put("company", company);
        integration.identify(new IdentifyPayloadBuilder().traits(traits).build());
        verify(intercom).updateUser(any(UserAttributes.class));
    }


    @Test
    public void track() {
        integration.track(new TrackPayloadBuilder().event("Foo").build());

        verify(intercom).logEvent("Foo");
    }

    @Test
    public void trackWithCustomProperties() {
        Properties properties = new Properties();
        properties.putValue("foo", "bar");
        integration.track(new TrackPayloadBuilder().event("Baz").properties(properties).build());

        verify(intercom).logEvent("Baz", properties.toStringMap());
    }

    @Test
    public void trackWithIllegalNestedProperties() {
        Properties properties = new Properties();
        Map <String, Object> bar = new HashMap<>();
        properties.putValue("foo", bar);
        properties.putValue("baz", "yo");
        integration.track(new TrackPayloadBuilder().event("Baz").properties(properties).build());

        properties.remove("foo");
        verify(intercom).logEvent("Baz", properties);
    }

    @Test
    public void trackWithRevenue() {
        Properties properties = new Properties();
        properties.putValue("orderId", "12345");
        properties.putValue("revenue", 100.0);
        properties.putCurrency("USD");
        Product product = new Product("456", "ABC", 100.0);
        Product product2 = new Product("789", "DEF", 100.0);
        properties.putProducts(product, product2);
        integration.track(new TrackPayloadBuilder().event("Order Completed").properties(properties).build());

        Map<String, Object> expectedProperties = new HashMap<>();
        expectedProperties.put("orderId", "12345");
        Map<String, Object> price = new HashMap<>();
        price.put("amount", 10000);
        price.put("currency", "USD");
        expectedProperties.put("price", price);

        verify(intercom).logEvent("Order Completed", expectedProperties);
    }

    @Test
    public void group() {
        Traits traits = new Traits();
        long createdAt = 123344L;
        int monthlySpend = 100;

        traits.put("name", "Acme");
        traits.put("createdAt", createdAt);
        traits.put("monthlySpend", monthlySpend);
        traits.put("plan", "startup");
        integration.group(new GroupPayloadBuilder().groupId("123").groupTraits(traits).build());
        verify(intercom).updateUser(any(UserAttributes.class));
    }

    @Test
    public void reset() {
        integration.reset();
        verify(intercom).logout();
    }
}