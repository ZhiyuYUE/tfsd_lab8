package fr.emse.Consolidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

public class Consolidator {

    public static void main(String[] args) {
        // AWS region
        Region region = Region.US_EAST_1;
        // Filename to be processed
        String filename = "01-10-2022-store1.csv";
        // AWS S3 bucket name
        String bucketName = "mybucketcloud131";
        // Create S3 client
        S3Client s3 = S3Client.builder().region(region).build();

        // Check if the specified file exists in the S3 bucket
        ListObjectsRequest listObjects = ListObjectsRequest.builder().bucket(bucketName).build();
        ListObjectsResponse res = s3.listObjects(listObjects);
        List<S3Object> objects = res.contents();

        if (objects.stream().anyMatch((S3Object x) -> x.key().equals(filename))) {
            try {
                // Get the content of the specified file from S3
                GetObjectRequest objRequest = GetObjectRequest.builder().key(filename).bucket(bucketName).build();
                ResponseInputStream<GetObjectResponse> oldData = s3.getObject(objRequest);
                BufferedReader oldReader = new BufferedReader(new InputStreamReader(oldData));
                // Process the CSV data from the file
                processCSVData(oldReader);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.println("The file you asked doesn't exist.");
        }
    }

    private static void processCSVData(BufferedReader oldReader) throws IOException {
        // Process the CSV data from the file and extract relevant information
        try {
            // Read the header and data arrays from the CSV file
            String[] header = oldReader.readLine().split(";");
            String[] type = oldReader.readLine().split(";");
            String[] profit = oldReader.readLine().split(";");
            String[] quantity = oldReader.readLine().split(";");
            String[] sold = oldReader.readLine().split(";");

            // Display consolidated store and product information
            displayStoreInfo(header, type, profit, quantity, sold);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void displayStoreInfo(String[] header, String[] type, String[] profit, String[] quantity,
            String[] sold) {
        // Display consolidated store information and calculate totals
        double totalProfit = 0;
        int leastStore = 0;
        double leastStoreProfit = Double.POSITIVE_INFINITY;
        int mostStore = 0;
        double mostStoreProfit = 0;

        for (int i = 0; i < header.length; i++) {
            if (type[i].equals("store")) {
                double storeProfit = Double.parseDouble(profit[i]);
                totalProfit += storeProfit;

                if (storeProfit < leastStoreProfit) {
                    leastStore = i;
                    leastStoreProfit = Double.parseDouble(profit[leastStore]);
                } else if (storeProfit > mostStoreProfit) {
                    mostStore = i;
                    mostStoreProfit = Double.parseDouble(profit[mostStore]);
                }
            } else {
                // Display product-specific information
                displayProductInfo(header[i], quantity[i], sold[i], profit[i]);
            }
        }

        // Display consolidated store information
        System.out.println("Total retailer profit: " + totalProfit);
        System.out.println("Least profitable store: " + header[leastStore] + " with a profit of " + leastStoreProfit);
        System.out.println("Most profitable store: " + header[mostStore] + " with a profit of " + mostStoreProfit);
    }

    private static void displayProductInfo(String header, String quantity, String sold, String profit) {
        // Display product-specific information
        System.out.println("Total quantity, total sold, and total profit for " + header + ": " + quantity + " " + sold
                + " " + profit);
    }
}


