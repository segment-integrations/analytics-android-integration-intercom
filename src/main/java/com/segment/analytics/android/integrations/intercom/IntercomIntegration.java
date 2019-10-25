package com.segment.analytics.android.integrations.intercom;

import android.app.Application;
import androidx.annotation.Nullable;

import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.Traits;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.GroupPayload;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.TrackPayload;

import java.util.Collection;
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
public class IntercomIntegration extends Integration<Intercom> {

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
  private static final String AMOUNT = "amount";
  private static final String CURRENCY = "currency";

  // Intercom specced user attributes
  private static final String EMAIL = "email";
  private static final String PHONE = "phone";
  private static final String LANGUAGE_OVERRIDE = "languageOverride";
  private static final String UNSUBSCRIBED_FROM_EMAILS = "unsubscribedFromEmails";

  // Intercom specced group attributes
  private static final String MONTHLY_SPEND = "monthlySpend";
  private static final String PLAN = "plan";

  // Segment specced properties
  private static final String REVENUE = "revenue";
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

  public IntercomIntegration(
      Provider provider, Application application, ValueMap settings, Logger logger) {
    String mobileApiKey = settings.getString("mobileApiKey");
    String appId = settings.getString("appId");

    Intercom.initialize(application, mobileApiKey, appId);
    this.intercom = provider.get();
    this.logger = logger;
  }

  @Override
  public void identify(IdentifyPayload identify) {
    super.identify(identify);

    String userId = identify.userId();

    String email = identify.traits().email();
    if (isNullOrEmpty(userId)) {
      intercom.registerUnidentifiedUser();
      logger.verbose("Intercom.client().registerUnidentifiedUser()");
    } else if(!isNullOrEmpty(email)) {
      Registration registration = Registration.create().withUserId(userId);
      intercom.registerIdentifiedUser(registration);
      logger.verbose("Intercom.client().registerIdentifiedUser(registration)");
    }

    Map<String, Object> intercomOptions = identify.integrations().getValueMap("Intercom");
    if (!isNullOrEmpty(intercomOptions)
        && !isNullOrEmpty(String.valueOf(intercomOptions.get("userHash")))) {
      intercom.setUserHash(String.valueOf(intercomOptions.get("userHash")));
    }

    Traits traits = identify.traits();
    if (!isNullOrEmpty(traits) && (!isNullOrEmpty(intercomOptions))) {
      setUserAttributes(traits, intercomOptions);
      return;
    }
    setUserAttributes(traits, null);
  }

  @Override
  public void track(TrackPayload track) {
    super.track(track);

    String eventName = track.event();
    Properties realProperties = track.properties();

    if (!isNullOrEmpty(realProperties)) {
      Properties propertiesCopy = new Properties();
      propertiesCopy.putAll(realProperties);
      Map<String, Object> price = new HashMap<>();
      Object revenueValue = propertiesCopy.get(REVENUE);
      Object totalValue = propertiesCopy.get(TOTAL);
      double amountDouble;
      int amountInCents;

      if ((revenueValue != null) && (revenueValue instanceof Double)) {
        amountDouble = (double) propertiesCopy.get(REVENUE);
        amountInCents = (int) amountDouble * 100;
        price.put(AMOUNT, amountInCents);
        propertiesCopy.remove(REVENUE);
      }
      if ((totalValue != null) && (totalValue instanceof Double) && (revenueValue == null)) {
        amountDouble = (double) propertiesCopy.get(TOTAL);
        amountInCents = (int) amountDouble * 100;
        price.put(AMOUNT, amountInCents);
        propertiesCopy.remove(TOTAL);
      }
      if (propertiesCopy.get(CURRENCY) != null) {
        price.put(CURRENCY, String.valueOf(propertiesCopy.get(CURRENCY)));
        propertiesCopy.remove(CURRENCY);
      }
      if (!isNullOrEmpty(price)) {
        propertiesCopy.put(PRICE, price);
      }
      for (Map.Entry<String, Object> entry : realProperties.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        if (key.equals("products") || value instanceof Map || value instanceof Collection) {
          propertiesCopy.remove(key);
        }
      }
      intercom.logEvent(eventName, propertiesCopy);
      logger.verbose("Intercom.client().logEvent(%s, %s)", eventName, propertiesCopy);
      return;
    }
    intercom.logEvent(eventName);
    logger.verbose("Intercom.client().logEvent(%s)", eventName);
  }

  @Override
  public void group(GroupPayload group) {
    super.group(group);

    if (isNullOrEmpty(group.groupId())) return;

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
    super.reset();
    intercom.logout();
    logger.verbose("Intercom.client().reset()");
  }

  @Override
  public Intercom getUnderlyingInstance() {
    return intercom;
  }

  private void setUserAttributes(Traits realTraits, @Nullable Map<String, Object> intercomOptions) {
    Traits traitsCopy = new Traits();
    traitsCopy.putAll(realTraits);
    traitsCopy.remove("userId");
    traitsCopy.remove("anonymousId");

    String name = traitsCopy.name();
    String email = traitsCopy.email();
    String phone = traitsCopy.phone();

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

    if (!isNullOrEmpty(intercomOptions)) {
      Object optionsUnsubscribedFromEmails = intercomOptions.get(UNSUBSCRIBED_FROM_EMAILS);
      Object optionsCreatedAt = intercomOptions.get(CREATED_AT);
      String languageOverride = String.valueOf(intercomOptions.get(LANGUAGE_OVERRIDE));

      if (!isNullOrEmpty(languageOverride)) {
        userAttributes.withLanguageOverride(languageOverride);
      }
      if (optionsCreatedAt != null && optionsCreatedAt instanceof Long) {
        long createdAt = (long) intercomOptions.get(CREATED_AT);
        userAttributes.withSignedUpAt(createdAt);
      }
      if (optionsUnsubscribedFromEmails != null
          && optionsUnsubscribedFromEmails instanceof Boolean) {
        boolean unsubscribedFromEmails = (boolean) intercomOptions.get(UNSUBSCRIBED_FROM_EMAILS);
        userAttributes.withUnsubscribedFromEmails(unsubscribedFromEmails);
      }
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
      if (!(value instanceof Map || value instanceof Collection)) {
        userAttributes.withCustomAttribute(trait, value);
      }
    }

    intercom.updateUser(userAttributes.build());
    logger.verbose("Intercom.client().updateUser(userAttributes)");
  }

  private Company setCompany(Map<String, Object> payload) {
    Company.Builder company = new Company.Builder();
    if (!payload.containsKey("id")) return company.build();
    company.withCompanyId(String.valueOf(payload.get("id")));
    payload.remove("id");

    if (payload.containsKey(NAME)) {
      String name = String.valueOf(payload.get(NAME));
      company.withName(name);
      payload.remove(NAME);
    }
    if (payload.containsKey(CREATED_AT)) {
      long createdAt = (long) payload.get(CREATED_AT);
      company.withCreatedAt(createdAt);
      payload.remove(CREATED_AT);
    }
    if (payload.containsKey(MONTHLY_SPEND)) {
      int monthlySpend = (int) payload.get(MONTHLY_SPEND);
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
      if (!(value instanceof Map || value instanceof Collection)) {
        company.withCustomAttribute(trait, value);
      }
    }
    return company.build();
  }
}
