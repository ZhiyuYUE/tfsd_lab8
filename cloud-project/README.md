Co-worker: Zhiyu YUE and Zekun REN
Date: 2024-1-8

Description:
To successfully set up and run the project, you have two options: either follow the steps in the demonstration video or adhere to the written instructions provided below:

Setting Up on AWS:

Create a new bucket, an SQS queue, and a Lambda function on AWS.
Modify your Java code in the Client, Worker, lambdaWorker, and Consolidator files. Update the bucketName and queueURL variables with the details of the bucket and queue you've just created. (Note: These variables might not be present in every file; in some cases, only one is used.)
Lambda Function Configuration:

Link your SQS queue to your Lambda function as a trigger.
Update the Lambda function's code by uploading the cloud-project/target/cloud-project-1.0-SNAPSHOT-jar-with-dependencies.jar file from the target folder.
Configure the Lambda function to use the handleRequest method from lambdaWorker when triggered.
Running the Client Application:

Open the Client.java file. Change the path and filename variables to match the file you wish to upload.
Execute the main function. The uploaded file will be processed by the SQS queue and Lambda function, which will generate or update a summary file for the date.
Consolidating Data:

Once all files for the day have been uploaded and processed, you can summarize the data. You can either run the Consolidator main function on your local machine (after modifying the filename for the date you're analyzing) or use an Amazon EC2 instance.
For local execution, simply run the Consolidator function.
For EC2 execution:
Create an EC2 instance.
Install Java using the following commands:
sudo dnf install java-17-amazon-corretto
sudo dnf install java-17-amazon-corretto-devel
Transfer the cloud-project/target/cloud-project-1.0-SNAPSHOT-jar-with-dependencies.jar file and your .aws folder (found in user/USERNAME on Windows) to the EC2 instance using SFTP as described here.
Run the Consolidator on the EC2 instance using the command:

java -cp cloud-project-1.0-SNAPSHOT-jar-with-dependencies.jar fr.emse.