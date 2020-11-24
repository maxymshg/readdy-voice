package com.github.maxymshg.readdy.voice;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.polly.AmazonPolly;
import com.amazonaws.services.polly.AmazonPollyClientBuilder;
import com.amazonaws.services.polly.model.OutputFormat;
import com.amazonaws.services.polly.model.StartSpeechSynthesisTaskRequest;
import com.amazonaws.services.polly.model.StartSpeechSynthesisTaskResult;
import com.amazonaws.services.polly.model.TextType;
import com.amazonaws.services.polly.model.VoiceId;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.event.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Handler implements RequestHandler<S3Event, String> {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final AmazonPolly AMAZON_POLLY_CLIENT = AmazonPollyClientBuilder.defaultClient();

    @Override
    public String handleRequest(S3Event s3event, Context context) {
        LambdaLogger logger = context.getLogger();

        try {
            logger.log("EVENT: " + gson.toJson(s3event));

            S3EventNotificationRecord record = s3event.getRecords().get(0);

            String srcBucket = record.getS3().getBucket().getName();

            // Object key may have spaces or unicode non-ASCII characters.
            String srcKey = record.getS3().getObject().getUrlDecodedKey();

            String dstBucket = "readdy-voice-content";
            String dstKey = srcKey;

            // Download the image from S3 into a stream
            AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
            S3Object s3Object = s3Client.getObject(new GetObjectRequest(srcBucket, srcKey));
            InputStream objectData = s3Object.getObjectContent();

            String text = new BufferedReader(new InputStreamReader(objectData, StandardCharsets.UTF_8))
	            .lines()
                .collect(Collectors.joining("\n"));


            StartSpeechSynthesisTaskRequest request = new StartSpeechSynthesisTaskRequest()
                .withOutputFormat(OutputFormat.Mp3.toString())
                .withText(text)
                .withTextType(TextType.Text)
                .withVoiceId(VoiceId.Amy)
                .withOutputS3BucketName(dstBucket);

            StartSpeechSynthesisTaskResult result = AMAZON_POLLY_CLIENT.startSpeechSynthesisTask(request);

            logger.log("Successfully converted " + srcBucket + "/" + srcKey + " and uploaded to " + dstBucket + "/"
                    + dstKey);
            
            return "Ok";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}