package io.odpf.firehose.sink.mongodb.request;

import io.odpf.firehose.config.MongoSinkConfig;
import io.odpf.firehose.config.enums.MongoSinkMessageType;
import io.odpf.firehose.config.enums.MongoSinkRequestType;
import io.odpf.firehose.metrics.Instrumentation;
import io.odpf.firehose.serializer.MessageToJson;
import lombok.AllArgsConstructor;

import java.util.ArrayList;

import static io.odpf.firehose.config.enums.MongoSinkRequestType.INSERT_OR_UPDATE;
import static io.odpf.firehose.config.enums.MongoSinkRequestType.UPDATE_ONLY;

/**
 * The Mongo request handler factory.
 */
@AllArgsConstructor
public class MongoRequestHandlerFactory {

    private final MongoSinkConfig mongoSinkConfig;
    private final Instrumentation instrumentation;
    private final String mongoPrimaryKey;
    private final MongoSinkMessageType messageType;
    private final MessageToJson jsonSerializer;

    /**
     * Gets request handler. This method returns the MongoDB update/upsert
     * request handler according to the value specified by the environment
     * variable SINK_MONGO_MODE_UPDATE_ONLY_ENABLE.
     * UpdateRequestHandler is returned if the SINK_MONGO_MODE_UPDATE_ONLY_ENABLE
     * is set to UPDATE_ONLY
     * UpsertRequestHandler is returned if the SINK_MONGO_MODE_UPDATE_ONLY_ENABLE
     * is set to INSERT_OR_UPDATE
     *
     * @return the MongoRequestHandler
     * @throws IllegalArgumentException if the sink type parameter specified in
     *                                  MongoSinkConfig, i.e. the value specified by the MongoRequestType
     *                                  is any value other than UPDATE_ONLY and INSERT_OR_UPDATE.
     * @since 0.1
     */
    public MongoRequestHandler getRequestHandler() {
        MongoSinkRequestType mongoSinkRequestType = mongoSinkConfig.isSinkMongoModeUpdateOnlyEnable() ? UPDATE_ONLY : INSERT_OR_UPDATE;
        instrumentation.logInfo("Mongo request mode: {}", mongoSinkRequestType);

        ArrayList<MongoRequestHandler> mongoRequestHandlers = new ArrayList<>();
        mongoRequestHandlers.add(new MongoUpdateRequestHandler(messageType, jsonSerializer, mongoSinkRequestType, mongoPrimaryKey));
        mongoRequestHandlers.add(new MongoUpsertRequestHandler(messageType, jsonSerializer, mongoSinkRequestType, mongoPrimaryKey));

        return mongoRequestHandlers
                .stream()
                .filter(MongoRequestHandler::canCreate)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Mongo Request Type " + mongoSinkRequestType.name() + " not supported"));
    }
}
