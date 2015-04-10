var fs = require('fs'),
    xml2js = require('xml2js');
 
var parser = new xml2js.Parser();

var entityMap = {
  'film': 'movie:film',
  'actor': 'movie:actor',
  'director': 'movie:director',
  'producer': 'movie:producer',
  'production_company': 'movie:production_company',
  'writer': 'movie:writer'
}

for (var key in entityMap) {
  (function(key) {
      fs.readFile(__dirname + '/../linked_mdb/resource_urls/' + key, function(err, data) {
        parser.parseString(data, function (err, result) {
          var fileName = key + 'Ids.json';
          getObjectWithName(result, entityMap[key], process, fileName);
          console.log('Done with ' + key);
        });
      });
  })(key);
}

function getObjectWithName(obj, name, callback, fileName) {
  if (typeof obj === 'object') {
    for (var key in obj) {
      if (key === name && obj.hasOwnProperty(key)) {
        callback(obj[key], fileName);
        return;
      }
      getObjectWithName(obj[key], name, callback, fileName);
    }
  } else if (typeof obj === 'array') {
    for (var i = 0; i < obj.length; i++) {
      getObjectWithName(obj[i],name, callback, fileName);
    }
  }
}

function process(obj, fileName) {
  var ids = [];
  for (var i = 0; i < obj.length; i++) {
    ids.push(obj[i]['$']['rdf:about'].split('/').pop());
  } 
  var str = JSON.stringify(ids);
  writeFileWithName(str, fileName);
}

function writeFileWithName(str, fileName) {
  fs.writeFile(__dirname + '/../linked_mdb/ids/' + fileName, str, function(err) {
    if(err) {
        return console.log(err);
    }
    console.log('The file ' + fileName + ' was saved!');
  }); 
}

