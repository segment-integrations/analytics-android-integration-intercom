package com.segment.analytics.android.integration.intercom;

import android.app.Application;

import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
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
import org.mockito.Mockito;
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
@PrepareForTest({Intercom.class, UserAttributes.class})
public class IntercomTest {
    @Rule public PowerMockRule rule = new PowerMockRule();
    @Mock Application application;
    @Mock UserAttributes.Builder builder;
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
                .putValue("apiKey", "123")
                .putValue("appId", "123"),
                Logger.with(VERBOSE));
        Mockito.reset(intercom);
    }

    @Test
    public void initialize() {
        integration = new IntercomIntegration(mockProvider, application, new ValueMap()
                .putValue("apiKey", "123")
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
    public void identifyWithSpeccedAttributes() {
        long createdAt = 123344L;
        Traits traits = createTraits("123");
        traits.putName("Brennan");
        traits.putEmail("testing@segment.com");
        traits.putPhone("1112223333");
        traits.putValue("languageOverride", "testing");
        traits.putValue("createdAt", createdAt);
        traits.putValue("unsubscribedFromEmails", true);
        integration.identify(new IdentifyPayloadBuilder().traits(traits).build());
        verify(intercom).updateUser(any(UserAttributes.class));
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
        Properties properties = new Properties();
        integration.track(new TrackPayloadBuilder().event("Foo").properties(properties).build());

        verify(intercom).logEvent("Foo", properties.toStringMap());
    }

    @Test
    public void trackWithCustomProperties() {
        Properties properties = new Properties();
        properties.putValue("foo", "bar");
        integration.track(new TrackPayloadBuilder().event("Baz").properties(properties).build());

        verify(intercom).logEvent("Baz", properties.toStringMap());
    }

    @Test
    public void trackWithRevenue() {
        Properties properties = new Properties();
        properties.putRevenue(100.0);
        properties.putCurrency("USD");
        integration.track(new TrackPayloadBuilder().event("Baz").properties(properties).build());

        Map<String, Object> eventProperties = new HashMap<>();
        Map<String, Object> price = new HashMap<>();
        price.put("revenue", 100.0);
        price.put("currency", "USD");
        eventProperties.put("price", price);

        verify(intercom).logEvent("Baz", eventProperties);
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
        verify(intercom).reset();
    }
}
