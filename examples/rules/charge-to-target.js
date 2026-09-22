// The pattern for a charging automation: derive "parked at home" from the car's position and
// stop the charger when the state of charge reaches a target. The state of charge is what the
// car last reported, so check its age before acting on it; while charging the binding wakes the
// car every wakeWhileCharging minutes, so the value is at most that old.
const { rules, triggers, items, time } = require('openhab');

const HOME = { lat: 55.000000, lon: 10.000000 };   // your house
const HOME_RADIUS_M = 150;
const TARGET_SOC = 80;
const MAX_AGE_MIN = 30;

function distanceM(lat1, lon1, lat2, lon2) {
  const r = 6371000, dLat = (lat2 - lat1) * Math.PI / 180, dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(a));
}

rules.JSRule({
  name: 'Car at home from its parked position',
  triggers: [triggers.ItemStateChangeTrigger('Car_Position')],
  execute: () => {
    const [lat, lon] = String(items.getItem('Car_Position').state).split(',').map(Number);
    if (!isFinite(lat) || !isFinite(lon)) return;
    items.getItem('Car_At_Home').postUpdate(distanceM(lat, lon, HOME.lat, HOME.lon) <= HOME_RADIUS_M ? 'ON' : 'OFF');
  }
});

rules.JSRule({
  name: 'Car charge to target',
  triggers: [triggers.ItemStateChangeTrigger('Car_SoC')],
  execute: () => {
    const soc = parseFloat(items.getItem('Car_SoC').state);
    const reported = items.getItem('Car_Battery_At').state;
    if (isNaN(soc) || reported === 'NULL') return;
    const ageMin = (Date.now() - time.toZDT(reported).toInstant().toEpochMilli()) / 60000;
    if (ageMin > MAX_AGE_MIN) return;               // stale: do not act on it
    if (items.getItem('Car_At_Home').state !== 'ON') return;
    if (soc >= TARGET_SOC && items.getItem('Car_Charging').state === 'ON') {
      console.info('Car at ' + soc + ' %: target reached, stopping the charger');
      items.getItem('Charger_Allow').sendCommand('OFF');   // your charger's item
    }
  }
});
