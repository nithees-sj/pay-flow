CREATE TABLE consumed_events(event_id UUID PRIMARY KEY,event_type VARCHAR(32) NOT NULL,schema_version INT NOT NULL,processed_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE notifications(id UUID PRIMARY KEY,event_id UUID NOT NULL REFERENCES consumed_events(event_id),user_id UUID NOT NULL,payment_id UUID NOT NULL,type VARCHAR(32) NOT NULL,message_key VARCHAR(64) NOT NULL,message_parameters JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),read_at TIMESTAMPTZ,UNIQUE(event_id,user_id));
CREATE INDEX notifications_history ON notifications(user_id,created_at DESC,id DESC);
CREATE INDEX notifications_unread ON notifications(user_id) WHERE read_at IS NULL;
