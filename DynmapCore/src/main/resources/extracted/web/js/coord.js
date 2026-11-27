componentconstructors['coord'] = function(dynmap, configuration) {
	// nerdlabs: the 3D view can only guess the height under the mouse. Use the world's spawn height rather than
	// sea level, so worlds built high up (or deep down) give much closer x/z values. Flat view x/z are exact.
	var refHeight = function() {
		var w = dynmap.world;
		if (w && w.center && typeof w.center.y === 'number') return Math.round(w.center.y);
		return w.sealevel + 1;
	};

	var Coord = L.Control.extend({
		valfield: $('<span/>'),
		mcrfield: $('<span/>'),
		chunkfield: $('<span/>'),

		onAdd: function(map) {
			if(configuration.hidey) 
				this._container = L.DomUtil.create('div', 'coord-control coord-control-noy');
			else
				this._container = L.DomUtil.create('div', 'coord-control');
			this._map = map;
			$('<span/>').addClass('coord-control-label').text((configuration.label || 'x,y,z') + ': ').appendTo(this._container);
			$('<br/>').appendTo(this._container);
			this.valfield.addClass('coord-control-value').text(configuration.hidey ? '---,---' : '---,---,---').appendTo(this._container);
			if(configuration['show-mcr']) {
				$('<br/>').appendTo(this._container);
				this.mcrfield.addClass('coord-control-value').text('--------').appendTo(this._container);
			}
			if(configuration['show-chunk']) {
				$('<br/>').appendTo(this._container);
				this.chunkfield.addClass('coord-control-value').text('Chunk: ---,---').appendTo(this._container);
			}
			this._update();
			return this.getContainer();
		},

		getPosition: function() {
			return 'topleft';
		},

		getContainer: function() {
			return this._container;
		},

		_update: function() {
			if (!this._map) return;
		}
	});

	var coord = new Coord();
	dynmap.map.addControl(coord);
	dynmap.map.on('mousemove', function(mevent) {
		if(!dynmap.map) return;
		var loc = dynmap.getProjection().fromLatLngToLocation(mevent.latlng, refHeight());
		if(configuration.hidey)
			coord.valfield.text(Math.round(loc.x) + ',' + Math.round(loc.z));
		else
			coord.valfield.text(Math.round(loc.x) + ',' + loc.y + ',' + Math.round(loc.z));
		if(configuration['show-mcr'])
			coord.mcrfield.text('r.' + Math.floor(loc.x/512) + '.' + Math.floor(loc.z/512) + '.mca');
		if(configuration['show-chunk'])
			coord.chunkfield.text('Chunk: ' + Math.floor(loc.x/16) + ',' + Math.floor(loc.z/16));
	});
	dynmap.map.on('mouseout', function(mevent) {
		if(!dynmap.map) return;
		if(configuration.hidey)
			coord.valfield.text('---,---');
		else
			coord.valfield.text('---,---,---');
		if(configuration['show-mcr'])
			coord.mcrfield.text('--------');
		if(configuration['show-chunk'])
			coord.chunkfield.text('Chunk: ---,---');
	});

	// nerdlabs: "Saved spot" box - click the map to keep a location, with a Copy button (plain "x y z")
	var Saved = L.Control.extend({
		onAdd: function(map) {
			var me = this;
			this._container = L.DomUtil.create('div', 'coord-control saved-control');
			L.DomEvent.disableClickPropagation(this._container);
			$('<span/>').addClass('coord-control-label').text('Saved spot:').appendTo(this._container);
			$('<br/>').appendTo(this._container);
			this.valfield = $('<span/>').addClass('coord-control-value').text('click the map').appendTo(this._container);
			this.copybtn = $('<button type="button"/>').addClass('saved-copy').text('Copy').attr('title', 'Copy x y z')
				.prop('disabled', true)
				.on('click', function(e) { e.preventDefault(); me.copy(); })
				.appendTo(this._container);
			return this._container;
		},
		getPosition: function() { return 'topleft'; },
		set: function(text) {
			this.text = text;
			this.valfield.text(text.replace(/ /g, ', '));
			this.copybtn.prop('disabled', false).text('Copy');
		},
		clear: function() {
			this.text = null;
			this.valfield.text('click the map');
			this.copybtn.prop('disabled', true).text('Copy');
		},
		copy: function() {
			var me = this, text = this.text;
			if (!text) return;
			var done = function() {
				me.copybtn.text('Copied!');
				setTimeout(function() { me.copybtn.text('Copy'); }, 1500);
			};
			// The clipboard API only works on https or localhost; fall back to the old way otherwise.
			if (navigator.clipboard && window.isSecureContext) {
				navigator.clipboard.writeText(text).then(done, function() { me.copyFallback(text) && done(); });
			} else if (me.copyFallback(text)) {
				done();
			}
		},
		copyFallback: function(text) {
			var ta = $('<textarea/>').val(text).css({ position: 'fixed', top: '-1000px', opacity: 0 }).appendTo('body');
			ta[0].select();
			var ok = false;
			try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
			ta.remove();
			return ok;
		}
	});

	var saved = new Saved();
	dynmap.map.addControl(saved);
	var savedPin = null;
	dynmap.map.on('click', function(mevent) {
		if(!dynmap.map) return;
		var loc = dynmap.getProjection().fromLatLngToLocation(mevent.latlng, refHeight());
		var x = Math.round(loc.x), y = Math.round(loc.y), z = Math.round(loc.z);
		saved.set(configuration.hidey ? (x + ' ' + z) : (x + ' ' + y + ' ' + z));
		if (savedPin) dynmap.map.removeLayer(savedPin);
		savedPin = L.circleMarker(mevent.latlng, { radius: 6, color: '#fff', weight: 2, fillColor: '#ff3d7f', fillOpacity: 0.9 }).addTo(dynmap.map);
	});
	// A saved spot belongs to one world: clear it when switching worlds.
	$(dynmap).bind('worldchanged', function() {
		saved.clear();
		if (savedPin) { dynmap.map.removeLayer(savedPin); savedPin = null; }
	});
};
