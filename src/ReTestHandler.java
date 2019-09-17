import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.client.TcpRawHttpClient;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ReTestHandler {
    private Map<String, String> requestMap;
    private File requests;
    private Table<String, String, String> loggedErrors;
    private TcpRawHttpClient client;
    private RawHttp http;
    private Integer noLogs = 1;

    ReTestHandler(File requests) throws FileNotFoundException {
        this.requests = requests;
        this.client = new TcpRawHttpClient();
        this.http = new RawHttp();
        this.requestMap = new HashMap<>();
        this.loggedErrors = HashBasedTable.create();
        this.importRequests();
    }

    void replayWithOptions() throws IOException {
        for(Map.Entry<String, String> entry : requestMap.entrySet()){
            RawHttpRequest request = http.parseRequest(entry.getKey());
            RawHttpResponse<?> response = client.send(request);
            checkResponseOptions(entry.getValue(), entry.getKey(), response);
        }
        saveResults();
    }

    void replayNoOptions() throws IOException {
        for(Map.Entry<String, String> entry : requestMap.entrySet()){
            RawHttpRequest request = http.parseRequest(entry.getKey());
            RawHttpResponse<?> response = client.send(request);
            loggedErrors.put(noLogs.toString(), noLogs.toString() + ". Request: \n\n" + request.toString(),
                    noLogs.toString() + ". Response: \n\n" + response.toString());
            noLogs++;
        }
        saveResults();
    }

    private void importRequests() throws FileNotFoundException {
        String request;
        String options;
        Scanner scanner = new Scanner(this.requests);
        scanner.useDelimiter("--options|--nextRequest");
        while(scanner.hasNext()) {
            request = scanner.next();
            options = scanner.next();
            requestMap.put(request, options);
        }
    }

    private void checkResponseOptions(String options, String request, RawHttpResponse response){
        Scanner scanner = new Scanner(options);
        scanner.useDelimiter("\n|:|\r\n");
        while(scanner.hasNext()){
            String option = scanner.next();
            if(option.equalsIgnoreCase("AssertHeader")){
                String headerField  = scanner.next();
                String headerFieldValue = scanner.next();
                assertEquals(headerFieldValue,
                        response.getHeaders().getFirst(headerField).orElse(""), request, response, headerField);
            } else if(option.equalsIgnoreCase("AssertStatusCode")){
                String statusCode = scanner.next();
                assertEquals(statusCode, Integer.valueOf(response.getStatusCode()).toString(), request, response, "Statuscode");
            } else if(option.equalsIgnoreCase("ContainsRegex")){
                String regexString = scanner.next();
                assertBodyContains(regexString, request, response);
            }
        }
    }

    private void assertEquals(String s, String orElse, String request, RawHttpResponse response, String headerField) {
        if(!s.equalsIgnoreCase(orElse)){
            String errMsg = headerField + " was expected to be '" + s + "' but was: '" + orElse + "'\n\n=========================" +
                    "==============================";
            loggedErrors.put(noLogs.toString() + ". Error:" + errMsg + "\n\n",  noLogs.toString() + ". Request: \n\n" + request.toString(),
                    noLogs.toString() + ". Response: \n\n" + response.toString() + "\n========================= +" +
                            "===============================");
            noLogs++;
        }
    }

    private void assertBodyContains(String regexString, String request, RawHttpResponse response){
        Pattern regexPattern = Pattern.compile(regexString);
        Matcher matcher = regexPattern.matcher(response.getBody().toString());
        boolean matches = matcher.matches();
        if(!matches){
            String errMsg = regexString + " did not match in HTTP-Response-Body\n\n===========================" +
                    "==============================";
            loggedErrors.put(noLogs.toString() + ". Error:" + errMsg + "\n\n",  noLogs.toString() + ". Request: \n\n" + request.toString(),
                    noLogs.toString() + ". Response-Body: \n\n" + response.getBody().toString() + "\n============================" +
                            "===============================");
            noLogs++;
        }
    }

    private void saveResults(){
        Path path = Paths.get("results_" + System.currentTimeMillis()/1000 + ".txt");
        try{
            Files.createFile(path);
            Files.writeString(path, loggedErrors.toString(), StandardOpenOption.APPEND);
        } catch (IOException e){
            System.out.println("An Exception occured when trying to write File: " + path.toString());
            System.out.println("ErrMsg: " +  e.getMessage() + "\n");
            System.out.println("Results printed to Console because File Operation Failed! \n\n");
            System.out.println(loggedErrors.toString());
        }

    }


}
