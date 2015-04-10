var fs = require('fs'),
    xml2js = require('xml2js');
 
var parser = new xml2js.Parser();

var finalFilmObj;

fs.readFile(__dirname + '/linked_mdb/films/1437', function(err, data) {
    parser.parseString(data, function (err, result) {
      getObjectWithName(result, 'movie:film', processFilm);
      
      console.log('Done');
    });
});

function getObjectWithName(obj, name, callback) {
  if (typeof obj === 'object') {
    for (var key in obj) {
      if (key === name && obj.hasOwnProperty(key)) {
        callback(obj[key]);
        return;
      }
      getObjectWithName(obj[key], name, callback);
    }
  } else if (typeof obj === 'array') {
    for (var i = 0; i < obj.length; i++) {
      getObjectWithName(obj[i],name, callback);
    }
  }
}

function processFilm(film) {
  console.log('film: ' + film);
  getObjectWithName(film, 'movie:filmid', getFilmId);
  getObjectWithName(film, 'dc:title', getFilmId);
}

function getFilmId(filmIdObj) {
  console.log('filmIdObj: ' + filmIdObj);
}

function getFilmTitle(filmTitleObj) {
  console.log('filmTitleObj: ' + filmTitleObj);
}