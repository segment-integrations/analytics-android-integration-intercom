package com.segment.analytics.android.integrations.intercom;

import android.app.Application;

import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.Traits;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.GroupPayload;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.TrackPayload;

import java.util.HashMap;
import java.util.Map;

import io.intercom.android.sdk.Company;
import io.intercom.android.sdk.Intercom;
import io.intercom.android.sdk.UserAttributes;
import io.intercom.android.sdk.identity.Registration;

import static com.segment.analytics.internal.Utils.isNullOrEmpty;

/**
 * Intercom is your direct line of communication to every user, right inside your app. Intercom's
 * in-app messages are up to 10 times more effective than email too! Send the right messages, to the
 * right users, at exactly the right time.
 *
 * @see <a href="https://developers.intercom.com/v2.0/docs/android-installation">Intercom for
 *     Android</a>
 */
public class IntercomIntegration extends Integration<Void> {

    public static final Factory FACTORY =
            new Factory() {
                @Override
                public Integration<?> create(ValueMap settings, Analytics analytics) {
                    Logger logger = analytics.logger(INTERCOM_KEY);

                    Application application = analytics.getApplication();

                    return new IntercomIntegration(Provider.REAL, application, settings, logger);
                }

                @Override
                public String key() {
                    return INTERCOM_KEY;
                }
            };

    private final Intercom intercom;
    private static final String INTERCOM_KEY = "Intercom";
    private final Logger logger;

    // Intercom common specced attributes
    private static final String NAME = "name";
    private static final String CREATED_AT = "createdAt";
    private static final String COMPANY = "company";
    private static final String PRICE = "price";
    private static final String REVENUE = "revenue";
    private static final String CURRENCY = "currency";

    // Intercom specced user attributes
    private static final String EMAIL = "email";
    private static final String PHONE = "phone";
    private static final String LANGUAGE_OVERRIDE = "languageOverride";
    private static final String UNSUBSCRIBED_FROM_EMAILS = "unsubscribedFromEmails";

    // Intercom specced group attributes
    private static final String MONTHLY_SPEND = "monthlySpend";
    private static final String PLAN = "plan";

    // Segment specced constants
    private static final String TOTAL = "total";

    public interface Provider {

        Intercom get();

        Provider REAL =
                new Provider() {
                    @Override
                    public Intercom get() {
                        return Intercom.client();
                    }
                };
    }

    public IntercomIntegration(Provider provider, Application application, ValueMap settings, Logger logger) {
        String apiKey = settings.getString("apiKey");
        String appId = settings.getString("appId");

        Intercom.initialize(application, apiKey, appId);
        this.intercom = provider.get();
        this.logger = logger;
    }

    @Override
    public void identify(IdentifyPayload identify) {
        super.identify(identify);

        String userId = identify.userId();

        if (isNullOrEmpty(userId)) {
            intercom.registerUnidentifiedUser();
            logger.verbose("Intercom.client().registerUnidentifiedUser()");
        } else {
            Registration registration = Registration.create().withUserId(userId);
            intercom.registerIdentifiedUser(registration);
            logger.verbose("Intercom.client().registerIdentifiedUser(registration)");
        }

        Traits traits = identify.traits();
        if (!isNullOrEmpty(traits)) {
            setUserAttributes(traits);
        }
    }

    @Override
    public void track(TrackPayload track) {
        super.track(track);

        String eventName = track.event();
        Properties properties = new Properties();
        properties.putAll(track.properties());
        Map<String, Object> price = new HashMap<>();
        Map<String, Object> propertiesMap = new HashMap<>();

        if (!isNullOrEmpty(properties)) {
            if (properties.get(REVENUE) != null) {
                price.put(REVENUE, properties.get(REVENUE));
                properties.remove(REVENUE);
            }
            if ((properties.get(TOTAL) != null) && (price.get(REVENUE) == null)) {
                price.put(TOTAL, properties.get(TOTAL));
                properties.remove(TOTAL);
            }
            if (properties.get(CURRENCY) != null) {
                price.put(CURRENCY, String.valueOf(properties.get(CURRENCY)));
                properties.remove(CURRENCY);
            }

            if (!isNullOrEmpty(price)) {
                propertiesMap.put(PRICE, price);
            }

            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String trait = entry.getKey();
                Object value = entry.getValue();
                propertiesMap.put(trait, value);
            }
        }
        intercom.logEvent(eventName, propertiesMap);
        logger.verbose("Intercom.client().logEvent(%s, %s)", eventName, propertiesMap);
    }

    @Override
    public void group(GroupPayload group) {
        super.group(group);

        if (isNullOrEmpty(group.userId())) return;

        UserAttributes.Builder userAttributes = new UserAttributes.Builder();
        Traits traits = new Traits();
        traits.putAll(group.traits());
        traits.put("id", group.groupId());
        Company company = setCompany(traits);
        userAttributes.withCompany(company);
        intercom.updateUser(userAttributes.build());
        logger.verbose("Intercom.client().updateUser(%s)", userAttributes);
    }

    public void reset() {
        intercom.reset();
        logger.verbose("Intercom.client().reset()");
    }

    private void setUserAttributes(Traits realTraits) {
        Traits traitsCopy = new Traits();
        traitsCopy.putAll(realTraits);
        traitsCopy.remove("userId");
        traitsCopy.remove("anonymousId");

        String name = traitsCopy.name();
        String email = traitsCopy.email();
        String phone = traitsCopy.phone();
        String languageOverride = traitsCopy.getString(LANGUAGE_OVERRIDE);

        UserAttributes.Builder userAttributes = new UserAttributes.Builder();

        if (!isNullOrEmpty(name)) {
            userAttributes.withName(name);
            traitsCopy.remove(NAME);
        }
        if (!isNullOrEmpty(email)) {
            userAttributes.withEmail(email);
            traitsCopy.remove(EMAIL);
        }
        if (!isNullOrEmpty(phone)) {
            userAttributes.withPhone(phone);
            traitsCopy.remove(PHONE);
        }
        if (!isNullOrEmpty(languageOverride)) {
            userAttributes.withLanguageOverride(languageOverride);
            traitsCopy.remove(LANGUAGE_OVERRIDE);
        }
        if (traitsCopy.containsKey(CREATED_AT)) {
            long signedUpAt = traitsCopy.getLong(CREATED_AT, 0);
            userAttributes.withSignedUpAt(signedUpAt);
            traitsCopy.remove(CREATED_AT);
        }
        if (traitsCopy.containsKey(UNSUBSCRIBED_FROM_EMAILS)) {
            boolean unsubscribedFromEmails = traitsCopy.getBoolean(UNSUBSCRIBED_FROM_EMAILS, false);
            userAttributes.withUnsubscribedFromEmails(unsubscribedFromEmails);
            traitsCopy.remove(UNSUBSCRIBED_FROM_EMAILS);
        }
        if (traitsCopy.containsKey(COMPANY) && traitsCopy.get(COMPANY) instanceof Map) {
            Map<String, Object> companyObj = (HashMap<String, Object>) traitsCopy.get(COMPANY);
            Company company = setCompany(companyObj);
            userAttributes.withCompany(company);
            traitsCopy.remove(COMPANY);
        }

        for (Map.Entry<String, Object> entry : traitsCopy.entrySet()) {
            String trait = entry.getKey();
            Object value = entry.getValue();
            userAttributes.withCustomAttribute(trait, value);
        }
        intercom.updateUser(userAttributes.build());
        logger.verbose("Intercom.client().updateUser(userAttributes)");
    }

    private Company setCompany(Map<String, Object> payload) {
        Company.Builder company = new Company.Builder();
        company.withCompanyId(String.valueOf(payload.get("id")));

        if (payload.containsKey(NAME)) {
            String name = String.valueOf(payload.get(NAME));
            company.withName(name);
            payload.remove(NAME);
        }
        if (payload.containsKey(CREATED_AT)) {
            Long createdAt = (long) payload.get(CREATED_AT);
            company.withCreatedAt(createdAt);
            payload.remove(CREATED_AT);
        }
        if (payload.containsKey(MONTHLY_SPEND)) {
            Integer monthlySpend = (int) payload.get(MONTHLY_SPEND);
            company.withMonthlySpend(monthlySpend);
            payload.remove(MONTHLY_SPEND);
        }
        if (payload.containsKey(PLAN)) {
            String plan = String.valueOf(payload.get(PLAN));
            company.withPlan(plan);
            payload.remove(PLAN);
        }

        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String trait = entry.getKey();
            Object value = entry.getValue();
            company.withCustomAttribute(trait, value);
        }
        return company.build();
    }
}