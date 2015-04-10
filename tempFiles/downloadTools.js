 // Dependencies
var fs = require('fs');
var url = require('url');
var http = require('http');
var exec = require('child_process').exec;
var spawn = require('child_process').spawn;

// App variables
var file_url = 'http://upload.wikimedia.org/wikipedia/commons/4/4f/Big%26Small_edit_1.jpg';
var DOWNLOAD_DIR = './downloads/';

// We will be downloading the files to a directory, so make sure it's there
// This step is not required if you have manually created the directory
var mkdir = 'mkdir -p ' + DOWNLOAD_DIR;
var child = exec(mkdir, function(err, stdout, stderr) {
    if (err) throw err;
    else download_file_httpget(file_url);
});

// Function to download file using HTTP.get
var download_file_httpget = function(file_url) {
var options = {
    host: url.parse(file_url).host,
    port: 80,
    path: url.parse(file_url).pathname
};

var file_name = url.parse(file_url).pathname.split('/').pop();
var file = fs.createWriteStream(DOWNLOAD_DIR + file_name);

http.get(options, function(res) {
    res.on('data', function(data) {
            file.write(data);
        }).on('end', function() {
            file.end();
            console.log(file_name + ' downloaded to ' + DOWNLOAD_DIR);
        });
    });
};


// Function to download file using curl
var download_file_curl = function(file_url) {

    // extract the file name
    var file_name = url.parse(file_url).pathname.split('/').pop();
    // create an instance of writable stream
    var file = fs.createWriteStream(DOWNLOAD_DIR + file_name);
    // execute curl using child_process' spawn function
    var curl = spawn('curl', [file_url]);
    // add a 'data' event listener for the spawn instance
    curl.stdout.on('data', function(data) { file.write(data); });
    // add an 'end' event listener to close the writeable stream
    curl.stdout.on('end', function(data) {
        file.end();
        console.log(file_name + ' downloaded to ' + DOWNLOAD_DIR);
    });
    // when the spawn child process exits, check if there were any errors and close the writeable stream
    curl.on('exit', function(code) {
        if (code != 0) {
            console.log('Failed: ' + code);
        }
    });
};  


// Function to download file using wget
var download_file_wget = function(file_url) {

    // extract the file name
    var file_name = url.parse(file_url).pathname.split('/').pop();
    // compose the wget command
    var wget = 'wget -P ' + DOWNLOAD_DIR + ' ' + file_url;
    // excute wget using child_process' exec function

    var child = exec(wget, function(err, stdout, stderr) {
        if (err) throw err;
        else console.log(file_name + ' downloaded to ' + DOWNLOAD_DIR);
    });
};