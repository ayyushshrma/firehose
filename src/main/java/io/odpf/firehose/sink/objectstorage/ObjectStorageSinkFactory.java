package io.odpf.firehose.sink.objectstorage;

import com.gojek.de.stencil.client.StencilClient;
import com.gojek.de.stencil.parser.ProtoParser;
import com.google.protobuf.Descriptors;
import io.odpf.firehose.config.ObjectStorageSinkConfig;
import io.odpf.firehose.metrics.Instrumentation;
import io.odpf.firehose.metrics.StatsDReporter;
import io.odpf.firehose.sink.Sink;
import io.odpf.firehose.sink.SinkFactory;
import io.odpf.firehose.sink.objectstorage.message.KafkaMetadataUtils;
import io.odpf.firehose.sink.objectstorage.message.MessageDeSerializer;
import io.odpf.firehose.sink.objectstorage.proto.KafkaMetadataProto;
import io.odpf.firehose.sink.objectstorage.proto.KafkaMetadataProtoFile;
import io.odpf.firehose.sink.objectstorage.proto.NestedKafkaMetadataProto;
import io.odpf.firehose.sink.objectstorage.writer.WriterOrchestrator;
import io.odpf.firehose.sink.objectstorage.writer.local.LocalFileWriterWrapper;
import io.odpf.firehose.sink.objectstorage.writer.local.TimePartitionPath;
import io.odpf.firehose.sink.objectstorage.writer.local.policy.SizeBasedRotatingPolicy;
import io.odpf.firehose.sink.objectstorage.writer.local.policy.TimeBasedRotatingPolicy;
import io.odpf.firehose.sink.objectstorage.writer.local.policy.WriterPolicy;
import io.odpf.firehose.sink.objectstorage.writer.remote.ObjectStorageWriterConfig;
import org.aeonbits.owner.ConfigFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ObjectStorageSinkFactory implements SinkFactory {
    @Override
    public Sink create(Map<String, String> configuration, StatsDReporter statsDReporter, StencilClient stencilClient) {
        ObjectStorageSinkConfig sinkConfig = ConfigFactory.create(ObjectStorageSinkConfig.class, configuration);

        Instrumentation instrumentation = new Instrumentation(statsDReporter, ObjectStorageSinkFactory.class);

        Path localBasePath = Paths.get(sinkConfig.getLocalDirectory());
        LocalFileWriterWrapper localFileWriterWrapper = getLocalFileWriterWrapper(sinkConfig, stencilClient);

        ObjectStorageWriterConfig objectStorageWriterConfig =
                new ObjectStorageWriterConfig(
                        localBasePath,
                        sinkConfig.getObjectStorageBucketName(),
                        sinkConfig.getStorageGcloudProjectID());

        WriterOrchestrator writerOrchestrator = new WriterOrchestrator(localFileWriterWrapper, objectStorageWriterConfig);
        MessageDeSerializer messageDeSerializer = getMessageDeSerializer(sinkConfig, stencilClient);
        return new ObjectStorageSink(new Instrumentation(statsDReporter, ObjectStorageSink.class), sinkConfig.getSinkType().toString(), writerOrchestrator, messageDeSerializer);
    }

    private Descriptors.Descriptor getMetadataMessageDescriptor(ObjectStorageSinkConfig sinkConfig) {
        Descriptors.FileDescriptor fileDescriptor = KafkaMetadataProtoFile.createFileDescriptor(sinkConfig.getKafkaMetadataColumnName());
        return sinkConfig.getKafkaMetadataColumnName().isEmpty()
                ? fileDescriptor.findMessageTypeByName(KafkaMetadataProto.getTypeName())
                : fileDescriptor.findMessageTypeByName(NestedKafkaMetadataProto.getTypeName());

    }

    private MessageDeSerializer getMessageDeSerializer(ObjectStorageSinkConfig sinkConfig, StencilClient stencilClient) {
        ProtoParser protoParser = new ProtoParser(stencilClient, sinkConfig.getInputSchemaProtoClass());
        KafkaMetadataUtils kafkaMetadataUtils = new KafkaMetadataUtils(sinkConfig.getKafkaMetadataColumnName());
        return new MessageDeSerializer(kafkaMetadataUtils, sinkConfig.getWriteKafkaMetadata(), protoParser);
    }

    private LocalFileWriterWrapper getLocalFileWriterWrapper(ObjectStorageSinkConfig sinkConfig, StencilClient stencilClient) {
        Descriptors.Descriptor outputMessageDescriptor = stencilClient.get(sinkConfig.getInputSchemaProtoClass());
        Descriptors.Descriptor metadataMessageDescriptor = getMetadataMessageDescriptor(sinkConfig);

        TimePartitionPath timePartitionPath = new TimePartitionPath(
                sinkConfig.getKafkaMetadataColumnName(),
                sinkConfig.getTimePartitioningFieldName(),
                sinkConfig.getTimePartitioningDatePattern(),
                sinkConfig.getPartitioningType(),
                sinkConfig.getTimePartitioningTimeZone(),
                sinkConfig.getTimePartitioningDatePrefix(),
                sinkConfig.getTimePartitioningHourPrefix());


        List<WriterPolicy> writerPolicies = new ArrayList<>();
        writerPolicies.add(new TimeBasedRotatingPolicy(sinkConfig.getFileRotationDurationMillis()));
        writerPolicies.add(new SizeBasedRotatingPolicy(sinkConfig.getFileRationMaxSizeBytes()));

        Path localBasePath = Paths.get(sinkConfig.getLocalDirectory());

        return new LocalFileWriterWrapper(
                sinkConfig.getFileWriterType(),
                sinkConfig.getWriterPageSize(),
                sinkConfig.getWriterBlockSize(),
                outputMessageDescriptor,
                metadataMessageDescriptor.getFields(),
                localBasePath,
                writerPolicies,
                timePartitionPath);
    }
}
