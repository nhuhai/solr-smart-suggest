var fs = require('fs');
var url = require('url');
var http = require('http');
var exec = require('child_process').exec;
var spawn = require('child_process').spawn;

var IDS_DIR = __dirname + '/../linked_mdb/ids/';
var DOWNLOAD_DIR = __dirname + '/../linked_mdb/rdfs/'

var fileNames = fs.readdirSync(IDS_DIR);

var urlMap = {
  'filmIds.json': 'http://data.linkedmdb.org/data/film/',
  'actorIds.json': 'http://data.linkedmdb.org/data/actor/',
  'directorIds.json': 'http://data.linkedmdb.org/data/director/',
  'production_companyIds.json': 'http://data.linkedmdb.org/data/production_company/',
  'producerIds.json': 'http://data.linkedmdb.org/data/producer/',
  'writerIds.json': 'http://data.linkedmdb.org/data/writer/'
};

var downloadDirMap = {
  'filmIds.json': 'films',
  'actorIds.json': 'actors',
  'directorIds.json': 'directors',
  'production_companyIds.json': 'production_companies',
  'producerIds.json': 'producers',
  'writerIds.json': 'writers'
}


fileNames.forEach(function(fileName) {
  fs.readFile(IDS_DIR + fileName, function(err, data) {
      var ids = JSON.parse(data);
      ids.forEach(function(id) {
        var file_url = urlMap[fileName] + id;
        var outputPath = DOWNLOAD_DIR + downloadDirMap[fileName] + '/';
        console.log(file_url);
        //dowloadFile(file_url, outputPath);
      });
      console.log('Done with ' + fileName);
  });
});

// for (var i = 0; i < 1; i++) {
//   (function(count) {
//     var fileName = fileNames[i];
//     fs.readFile(IDS_DIR + fileName, function(err, data) {
//         var ids = JSON.parse(data);
//         for (var j = 20; j < ids.length; j++) {
//           (function(id) {
//             var file_url = urlMap[fileName] + id;
//             var outputPath = DOWNLOAD_DIR + downloadDirMap[fileName] + '/';
//             dowloadFile(file_url, outputPath);
//           })(ids[j]);
//         }
//         console.log('Done with ' + fileName);
//     });
//   })(i);
// }

function dowloadFile(file_url, outputPath) {
  var file_name = url.parse(file_url).pathname.split('/').pop();
  // var file = fs.createWriteStream(outputPath + file_name);
  // var curl = spawn('curl', [file_url]);

  // curl.stdout.on('data', function(data) { file.write(data); });
  // curl.stdout.on('end', function(data) {
  //     file.end();
  //     console.log(file_name + ' downloaded to ' + outputPath);
  // });

  // curl.on('exit', function(code) {
  //     if (code != 0) {
  //         console.log('Failed: ' + code);
  //     }
  // });

  var downloadedFiles = fs.readdirSync(outputPath);
  if (downloadedFiles.indexOf(file_name) === -1) {
    var wget = 'wget -P ' + outputPath + ' ' + file_url;
    var child = exec(wget, function(err, stdout, stderr) {
        if (err) throw err;
        else console.log(file_name);
    });
  }
}

