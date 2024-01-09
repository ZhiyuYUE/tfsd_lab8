package fr.emse.Worker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;


public class lambdaWorker implements RequestHandler<SQSEvent, Object> {
    @Override
    public SQSBatchResponse handleRequest(SQSEvent sqsEvent, Context context) {
	 	for(SQSMessage msg : sqsEvent.getRecords()){
            processMessage(msg.getBody());
        }
        return null;
    }
	 
	private static void processMessage(String fileName) {
		Region region = Region.US_EAST_1;
		String bucketName = "mybucketcloud131";
		S3Client s3 = S3Client.builder().region(region).build();
		
		// Check if file exists
		ListObjectsRequest listObjects = ListObjectsRequest.builder().bucket(bucketName).build();
		
		ListObjectsResponse res = s3.listObjects(listObjects);
		List<S3Object> objects = res.contents();			

		if (objects.stream().anyMatch((S3Object x) -> x.key().equals(fileName))) {
			// Retrieve file
			GetObjectRequest objectRequest = GetObjectRequest.builder().key(fileName).bucket(bucketName).build();
			ResponseInputStream<GetObjectResponse> data = s3.getObject(objectRequest);

			uploadData(s3, data, bucketName, objects);

		} else {
			System.out.println("File is not available in the Bucket");
		}
	}

    public static void uploadData(S3Client s3, ResponseInputStream<GetObjectResponse> data, String bucketName, List<S3Object> objects) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(data));
    
            // Skip the csv headers
            reader.readLine();
    
            Map<String, double[]> products = new HashMap<String, double[]>();
    
            // Extract metadata without moving the pointer
            reader.mark(1000);
            String[] head = extractMetadata(reader);
            reader.reset();
    
            // First data processing to find out the storeProfit and sum up data into 'products' (each item is associated to its [sold_quantity, money_made, profit_made] list)
            double storeProfit = processCSV(reader, products, head);

            // Update daily csv file
            String dailyName = head[0].replace("/", "-") + ".csv";
    
            updateDailyFile(s3, bucketName, objects, dailyName, products, storeProfit, head);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static String[] extractMetadata(BufferedReader reader) throws IOException {
        // The content returned has as first element the date and the second one, the store name
        String[] head = reader.readLine().split(";");
        return new String[]{head[0].split(" ")[0], head[1]};
    }
    
    private static Double processCSV(BufferedReader reader, Map<String, double[]> products, String[] head) throws IOException {
        // The function returns only the store profit for the day as the modifications on 'products' are still present outside the function (HashMap class).

        Double storeProfit = 0.;
        String line;

        while ((line = reader.readLine()) != null) {
            String[] row = line.split(";");
            storeProfit += Integer.parseInt(row[3]) * Double.parseDouble(row[6]);
    
            double[] prod = {Integer.parseInt(row[3]), Double.parseDouble(row[7]), Double.parseDouble(row[6]) * Integer.parseInt(row[3])};
            updateProductsMap(products, row[2], prod);
        }
        
        return storeProfit;
    }
    
    private static void updateProductsMap(Map<String, double[]> products, String key, double[] prod) {
        // For a given product 'prod', this function adds or updates the [sold_quantity, money_made, profit_made] double list of the product
        if (products.containsKey(key)) {
            double[] oldProd = products.get(key);
            oldProd[0] += prod[0];
            oldProd[1] += prod[1];
            oldProd[2] += prod[2];
            products.replace(key, oldProd);
        } else {
            products.put(key, prod);
        }
    }
     
    private static void updateDailyFile(S3Client s3, String bucketName, List<S3Object> objects, String dailyName, 
                                        Map<String, double[]> products, Double storeProfit, String[] head) throws IOException {

        /* The following lists work together to map the data, elements at a same index gives out information on the same object : 
        header[i] is the name of the store or name the a product, the related type[i] has either value 'store' or 'product' to difference the two possiblities,
        then profit, quantity and sold is the addition of every profit/quantity/sold_money made in a store or of a product (for a store, since quantity and sold
        aren't relevant information in this project, they are equal to 0)*/
        List<String> header = new ArrayList<>();
        List<String> type = new ArrayList<>();
        List<String> profit = new ArrayList<>();
        List<String> quantity = new ArrayList<>();
        List<String> sold = new ArrayList<>();

        // If the daily file already exists, updates header, type, profit, quantiy and sold with their previous values
        if (objects.stream().anyMatch(x -> x.key().equals(dailyName))) {
            updateFromExistingDailyFile(s3, bucketName, dailyName, header, type, profit, quantity, sold);
        }
    
        // If the store's document hasn't been treated before (because sqs could refeed seen data)
        if (!header.contains(head[1])) {
            updateStoreData(storeProfit, header, type, profit, quantity, sold, head);
            updateExistingProducts(header, quantity, sold, profit, products);
            addRemainingProducts(header, type, quantity, sold, profit, products);
    
            String newData = createCSVString(header, type, profit, quantity, sold);
            uploadDataToS3(s3, bucketName, dailyName, newData);
        }
        else{
            System.out.println("This data doesn't needed to be uploaded.");
        }
    }
    
    private static void updateFromExistingDailyFile(S3Client s3, String bucketName, String dailyName,
                                                    List<String> header, List<String> type, List<String> profit,
                                                    List<String> quantity, List<String> sold) throws IOException {
        // Looking for and updating the existing data of regarding the daily file

        GetObjectRequest objRequest = GetObjectRequest.builder().key(dailyName).bucket(bucketName).build();
        ResponseInputStream<GetObjectResponse> oldData = s3.getObject(objRequest);
        BufferedReader oldReader = new BufferedReader(new InputStreamReader(oldData));
    
        header.addAll(Arrays.asList(oldReader.readLine().split(";")));
        type.addAll(Arrays.asList(oldReader.readLine().split(";")));
        profit.addAll(Arrays.asList(oldReader.readLine().split(";")));
        quantity.addAll(Arrays.asList(oldReader.readLine().split(";")));
        sold.addAll(Arrays.asList(oldReader.readLine().split(";")));
    }
    
    private static void updateStoreData(double storeProfit, List<String> header, List<String> type, List<String> profit,
                                        List<String> quantity, List<String> sold, String[] head) {
        // Store the storeProfit along the store name in the lists

        header.add(head[1]);
        type.add("store");
        profit.add(String.valueOf(storeProfit));
        quantity.add("0");
        sold.add("0");
    }
    
    private static void updateExistingProducts(List<String> header, List<String> quantity, List<String> sold,
                                               List<String> profit, Map<String, double[]> products) {
        // This function updates the quantity, sold and profit values of an existing product (and then removes this product from the list of remaining products)

        for (int i = 0; i < header.size(); i++) {
            String valName = header.get(i);
            if (products.containsKey(valName)) {
                double[] prod = products.get(valName);
                quantity.set(i, String.valueOf(Double.parseDouble(quantity.get(i)) + prod[0]));
                sold.set(i, String.valueOf(Double.parseDouble(sold.get(i)) + prod[1]));
                profit.set(i, String.valueOf(Double.parseDouble(profit.get(i)) + prod[2]));
                products.remove(valName);
            }
        }
    }
    
    private static void addRemainingProducts(List<String> header, List<String> type, List<String> quantity,
                                             List<String> sold, List<String> profit, Map<String, double[]> products) {
        // Adding the name, type ('product'), quantity, sold and profit of the remaining products (no removal of products since the list is no longer useful)

        for (String key : products.keySet()) {
            double[] prod = products.get(key);
            header.add(key);
            type.add("product");
            quantity.add(String.valueOf(prod[0]));
            sold.add(String.valueOf(prod[1]));
            profit.add(String.valueOf(prod[2]));
        }
    }
    
    private static String createCSVString(List<String> header, List<String> type, List<String> profit,
                                          List<String> quantity, List<String> sold) {
        // Creating the csv document from the lists

        return String.join(";", header) + '\n' +
                String.join(";", type) + '\n' +
                String.join(";", profit) + '\n' +
                String.join(";", quantity) + '\n' +
                String.join(";", sold);
    }
    
    private static void uploadDataToS3(S3Client s3, String bucketName, String dailyName, String newData) {
        // Uploading the csv document in the bucket

        PutObjectRequest putOb = PutObjectRequest.builder().bucket(bucketName)
                .key(dailyName).build();
        s3.putObject(putOb, RequestBody.fromString(newData));

        System.out.println("Data has been uploaded.");
    }
}

