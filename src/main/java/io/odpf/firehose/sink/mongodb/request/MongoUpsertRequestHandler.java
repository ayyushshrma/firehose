package io.odpf.firehose.sink.mongodb.request;

import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import io.odpf.firehose.config.enums.MongoSinkMessageType;
import io.odpf.firehose.config.enums.MongoSinkRequestType;
import io.odpf.firehose.consumer.Message;
import io.odpf.firehose.serializer.MessageToJson;
import org.bson.Document;
import org.json.simple.JSONObject;

/**
 * The Mongo update request handler.
 * This class is responsible for creating requests when one
 * or more fields of a MongoDB document need to be updated,
 * if a document with that primary key already exists,
 * otherwise a new document is inserted into the MongoDB
 * collection.
 *
 * @since 0.1
 */
public class MongoUpsertRequestHandler extends MongoRequestHandler {

    private final MongoSinkRequestType mongoSinkRequestType;
    private final String mongoPrimaryKey;

    /**
     * Instantiates a new Mongo upsert request handler.
     *
     * @param messageType          the message type
     * @param jsonSerializer       the json serializer
     * @param mongoSinkRequestType the Mongo sink request type, i.e. UPDATE_ONLY/INSERT_OR_UPDATE
     * @param mongoPrimaryKey      the Mongo primary key
     * @since 0.1
     */
    public MongoUpsertRequestHandler(MongoSinkMessageType messageType, MessageToJson jsonSerializer, MongoSinkRequestType mongoSinkRequestType, String mongoPrimaryKey) {
        super(messageType, jsonSerializer);
        this.mongoSinkRequestType = mongoSinkRequestType;
        this.mongoPrimaryKey = mongoPrimaryKey;
    }

    @Override
    public boolean canCreate() {
        return mongoSinkRequestType == MongoSinkRequestType.INSERT_OR_UPDATE;
    }

    @Override
    public ReplaceOneModel<Document> getRequest(Message message) {
        String logMessage = extractPayload(message);
        JSONObject logMessageJSONObject = getJSONObject(logMessage);

        Document document = new Document("_id", getFieldFromJSON(logMessageJSONObject, mongoPrimaryKey));
        document.putAll(logMessageJSONObject);

        return new ReplaceOneModel<>(
                new Document("_id", getFieldFromJSON(logMessageJSONObject, mongoPrimaryKey)),
                document,
                new ReplaceOptions().upsert(true));
    }
}
