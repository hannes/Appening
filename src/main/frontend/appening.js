if (typeof (Number.prototype.toRad) === "undefined") {
	Number.prototype.toRad = function() {
		return this * Math.PI / 180;
	}
}

function dist(lat1, lon1, lat2, lon2) {
	var R = 6371; // km
	var dLat = (lat2 - lat1).toRad();
	var dLon = (lon2 - lon1).toRad();
	var lat1 = lat1.toRad();
	var lat2 = lat2.toRad();

	var a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.sin(dLon / 2)
			* Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
	var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	var d = R * c;
	return d;
}

function linkify(text) {
	text = text.replace(/(https?:\/\/\S+)/gi, function(s) {
		return '<a href="' + s + '">' + s + '</a>';
	});

	text = text.replace(/(^|)@(\w+)/gi, function(s) {
		return '<a href="http://twitter.com/' + s + '">' + s + '</a>';
	});

	text = text.replace(/(^|)#(\w+)/gi, function(s) {
		return '<a href="http://search.twitter.com/search?q='
				+ s.replace(/#/, '%23') + '">' + s + '</a>';
	});
	return text;
}

function generateWeb(place) {
	// TODO: replace with actual content
	return '<ul data-role="listview" data-inset="true">'
			+ '<li><a href="http://fronteers.nl/congres/2012/schedule">Fronteers 2012 Schedule</a></li>'
			+ '<li><p><img height="140px" src="testeventpic.jpg" />&nbsp;<img height="140px" src="testeventpic2.jpg" />&nbsp;<img height="140px" src="testeventpic3.jpg" /></p></li></ul>';
}

function trendFigure(trend) {
	var s = trend.s;
	var n = trend.n;

	if (n < 2) {
		return '<td style="color:gray;">-</td>';
	}
	var color = 'black';
	var prefix = '+';
	if (s < 0) {
		color = 'red';
		prefix = ''
	}

	return '<td><span style="color:' + color + ';">' + prefix + ''
			+ s.toFixed(2) + '</span> <small>(n=' + n + ')</small></td>';
}

function geoSuccess(position) {
	$.each($('#places').data('places'), function(index, place) {
		if ($('#' + place.id).length == 0) {
			return;
		}
		var distance = dist(position.coords.latitude,
				position.coords.longitude, place.lat, place.lng);
		distStr = distance.toFixed(1) + 'km';
		if (distance < 1) {
			distStr = Math.round(distStr * 1000) + 'm';
		}
		$('#' + place.id + ' .distance').text(distStr);
	});
}

function pageUpdater() {
	$.getJSON('data/places.json', function(data) {
		var items = [];

		$('#places').data('places', data);
		$.each(data, function(index, place) {
			if (place.trend.rank < 1 && place.id != showPlaceId) {
				return;
			}
			items.push(generateHtml(place));
		});

		if (items.length > 0) {
			$('#places .loading').hide();

			// populate list of places
			$('#places').html(items.join('\n')).trigger('create');

			// rig events
			$("#places .loadingMessages").bind("show", function() {
				var placeholder = $(this);
				var pid = placeholder.attr('data-id');
				loadMessages(pid, placeholder);
			});

			$("#places .loadingLocation").bind("show", function() {
				var placeholder = $(this);
				var pid = placeholder.attr('data-id');
				showMap(pid, placeholder);
			});
			if (showPlaceId != undefined && $('#' + showPlaceId).length > 0) {
				var ele = $('#' + showPlaceId);
				console.log(ele);
				ele.trigger('expand');
			}

			$('#posbutton').click(function() {
				navigator.geolocation.watchPosition(geoSuccess);
			});

		} else {
			// TODO: show some heartfelt message that we could not find anything
		}

		// TODO: really refresh periodically?
		// setTimeout(pageUpdater, 1 * 60 * 1000); // 1 min

	});

}

function generateTrends(place) {
	ret = '<div data-role="collapsible"><h2>Trends</h2><table><tr><th>48h</th><th>24h</th><th>12h</th><th>6h</th><th>3h</th><th>Rank</th></tr>';

	ret += trendFigure(place.trend.h48);
	ret += trendFigure(place.trend.h24);
	ret += trendFigure(place.trend.h12);
	ret += trendFigure(place.trend.h6);
	ret += trendFigure(place.trend.h3);
	ret += '<td>' + place.trend.rank.toFixed(2) + '</td>';

	ret += '</table>';

	chartSeries = place.mentionsSeries.slice().reverse();
	width = Math.min(Math.round($('#main').width() * 0.7), 400);
	height = Math.round(width * 0.3);
	ret += '<p><img src="http://chart.googleapis.com/chart?cht=ls&chxr=0,0,72&chxs=0,676767,0,0,_,676767&chm=D,0033FF,0,0,1,1&chs='
			+ width
			+ 'x'
			+ height
			+ '&chds=a&chd=t:'
			+ chartSeries.join(',')
			+ '&chxt=x,y" /></p>';
	ret += '</div>';

	return ret;
}

function generateHtml(place) {
	ret = '<div data-role="collapsible" id="'
			+ place.id
			+ '"><h2><span class="distance ui-li-count ui-btn-up-d ui-btn-corner-all"></span> '
			+ place.name + '</h2>';
	// TODO: re-enable once links, pictures and vieos are also exportet
	
	//ret += generateWeb(place);

	// rest is sub collapsible
	ret += '<div data-role="collapsible-set" data-mini="true" data-inset="true" data-theme="d" data-content-theme="d">';
	ret += '<div data-role="collapsible"><h2>Location</h2><p class="loadingLocation" data-id='
			+ place.id + '>loading...</p></div>';
	ret += '<div data-role="collapsible"><h2>Recent Tweets</h2><p class="loadingMessages" data-id='
			+ place.id + '>loading...</p></div>';
	ret += generateTrends(place);

	ret += '</div>'; // end sub collapsible
	ret += '</div>'; // end main collapsible

	return ret;
}

function showMap(pid, placeholder) {
	map = $('<div class="map"></div>');
	width = Math.min(Math.round($('#main').width() * 0.8), 600);
	map.width(width);
	map.height(Math.round(width * 0.6));
	map.gmap({
		'center' : '52.3741,4.897',
		'mapTypeControl' : false,
		'mapTypeId' : google.maps.MapTypeId.ROADMAP,
		'streetViewControl' : false,
		'maxZoom' : 16
	});

	place = findPlaceById(pid);

	var latlng = new google.maps.LatLng(place.lat, place.lng);

	map.gmap('addMarker', {
		'position' : latlng,
		'bounds' : true,
		'id' : pid
	});
	locArea = $('<div></div>');
	var addressEle = $('<p></p>');

	locArea.append(addressEle);
	locArea.append(map);
	$(placeholder).replaceWith(locArea);

	var geocoder = new google.maps.Geocoder();

	geocoder.geocode({
		'latLng' : latlng
	}, function(results, status) {
		if (status == google.maps.GeocoderStatus.OK) {
			if (results[0]) {
				addressEle.text(results[0].formatted_address);
			}
		}
	});
}

function findPlaceById(id) {
	places = $('#places').data('places');
	retplace = undefined;

	$.each(places, function(index, place) {
		if (place.id == id) {
			retplace = place;
		}
	});
	return retplace;
}

function loadMessages(pid, placeholder) {
	$.getJSON('data/' + pid + '-messages.json', function(data) {
		var msgs = [];
		$.each(data, function(index, message) {
			msgs.push('<li><h3>' + linkify('@' + message.user)
					+ '</h3><p style="white-space:normal;">'
					+ linkify(message.text)
					+ '</p><p class="ui-li-aside"><strong>'
					+ $.timeago(message.created) + '</strong></p></li>');
		});
		newList = $('<ul data-role="listview" data-inset="false">'
				+ msgs.join('') + '</ul>');
		$(placeholder).replaceWith(newList);
		newList.parent().trigger("create");
	});
}

// This allows us to bind events to a show event which fires as an element
// becomes visible
$(function() {
	$.each([ "toggleClass" ], function() {
		var _oldFn = $.fn[this];
		$.fn[this] = function() {
			var hidden = this.find(":hidden").add(this.filter(":hidden"));
			var visible = this.find(":visible").add(this.filter(":visible"));
			var result = _oldFn.apply(this, arguments);
			hidden.filter(":visible").each(function() {
				$(this).triggerHandler("show");
			});
			visible.filter(":hidden").each(function() {
				$(this).triggerHandler("hide");
			});
			return result;
		}
	});
});
