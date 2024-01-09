package fr.emse.Client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

public class Client {

  public static void main(String[] args) {
    // Set AWS region
    Region region = Region.US_EAST_1;

    // SQS Queue URL
    String queueURL = "https://sqs.us-east-1.amazonaws.com/096772867893/myqueue131";

    // S3 Bucket name
    String bucketName = "mybucketcloud131";

    // Path to the file to be uploaded
    String path = Path.of("D:\\3a\\major\\cloud\\Cloud_AWS-services\\data").toString();

    // Name of the file to be uploaded
    String filename = "01-10-2022-store10.csv";
    
    // Upload file to S3
    S3Client s3 = S3Client.builder().region(region).build();

    // Check if the bucket exists, if not, create it
    ListBucketsRequest listBucketsRequest = ListBucketsRequest.builder().build();
    ListBucketsResponse listBucketResponse = s3.listBuckets(listBucketsRequest);

    if ((listBucketResponse.hasBuckets()) && (listBucketResponse.buckets()
        .stream().noneMatch(x -> x.name().equals(bucketName)))) {

      CreateBucketRequest bucketRequest = CreateBucketRequest.builder().bucket(bucketName).build();
      s3.createBucket(bucketRequest);
    }

    // Specify the S3 PutObjectRequest
    PutObjectRequest putOb = PutObjectRequest.builder().bucket(bucketName).key(filename).build();

    // Upload the file to S3
    s3.putObject(putOb, RequestBody.fromBytes(getObjectFile(path + File.separator + filename)));
    
    // Send SQS notification
    sendNotification(queueURL, filename);
  }


  private static void sendNotification(String queueURL, String fileName) {
    Region region = Region.US_EAST_1;

    // Create an SQS client
    SqsClient sqsClient = SqsClient.builder().region(region).build();

    // Build SQS request to send a message with the filename to the specified queue
    SendMessageRequest sendRequest = SendMessageRequest.builder().queueUrl(queueURL)
        .messageBody(fileName).build();

    // Send the SQS message and get the response
    SendMessageResponse sqsResponse = sqsClient.sendMessage(sendRequest);

    // Print the result of sending the message
    System.out.println(
        sqsResponse.messageId() + " Message sent. Status is " + sqsResponse.sdkHttpResponse().statusCode());
  }


  // Converts file to bytes
  private static byte[] getObjectFile(String filePath) {

    FileInputStream fileInputStream = null;
    byte[] bytesArray = null;

    try {
      File file = new File(filePath);
      bytesArray = new byte[(int) file.length()];
      fileInputStream = new FileInputStream(file);
      fileInputStream.read(bytesArray);

    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      // Close the file input stream in a finally block to ensure it is always closed
      if (fileInputStream != null) {
        try {
          fileInputStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return bytesArray;
  }

}
