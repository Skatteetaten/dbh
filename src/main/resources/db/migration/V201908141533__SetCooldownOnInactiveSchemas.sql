update SCHEMA_DATA set SET_TO_COOLDOWN_AT=current_timestamp, DELETE_AFTER=current_timestamp + interval '61' day where ACTIVE=0;