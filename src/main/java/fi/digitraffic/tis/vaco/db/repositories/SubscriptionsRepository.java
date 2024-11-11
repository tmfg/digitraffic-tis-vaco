package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.notifications.SubscriptionRecord;
import fi.digitraffic.tis.vaco.notifications.model.SubscriptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SubscriptionsRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;

    public SubscriptionsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SubscriptionRecord> createSubscription(SubscriptionType type, CompanyRecord subscriber, CompanyRecord resource) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                INSERT INTO subscription(type, subscriber_id, resource_id)
                     VALUES (?::subscription_type, ?, ?)
                  RETURNING id,
                            public_id,
                            type,
                            subscriber_id,
                            resource_id
                """,
                RowMappers.SUBSCRIPTION_RECORD,
                type.fieldName(),
                subscriber.id(),
                resource.id()));
        } catch (DataAccessException dae) {
            logger.warn("Failed to create Subscription", dae);
            return Optional.empty();
        }
    }

    public List<SubscriptionRecord> findSubscriptions(CompanyRecord subscriber) {
        try {
            return jdbc.query("SELECT * FROM subscription WHERE subscriber_id = ?", RowMappers.SUBSCRIPTION_RECORD, subscriber.id());
        } catch (DataAccessException dae) {
            return List.of();
        }
    }

    public boolean deleteByPublicId(String publicId) {
        return jdbc.update("DELETE FROM subscription WHERE public_id = ?", publicId) > 0;
    }

    /**
     * Find all subscriptions for specified resource. In terms of subscription, resource means the subscription target.
     *
     * @param subscriptionType Subscription type filter.
     * @param resource Reference of the company resource we want to list the subscriptions for.
     * @return
     */
    public List<SubscriptionRecord> findSubscriptionsForResource(SubscriptionType subscriptionType, CompanyRecord resource) {
        try {
            return jdbc.query(
                """
                SELECT *
                  FROM subscription
                  WHERE type = ?::subscription_type
                    AND resource_id = ?
                """,
                RowMappers.SUBSCRIPTION_RECORD,
                subscriptionType.fieldName(),
                resource.id()
            );
        } catch (DataAccessException dae) {
            return List.of();
        }
    }
}
