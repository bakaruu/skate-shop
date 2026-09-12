CREATE TABLE processed_order_events (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    order_id BIGINT NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_processed_order_events_topic_order UNIQUE (topic, order_id)
);
