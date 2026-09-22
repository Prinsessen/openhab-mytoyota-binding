// Start the climate 20 minutes before a weekday departure, only when the car is parked
// at home. Car_At_Home is a Switch you set from Car_Position (see charge-to-target.js
// for the distance check). The car uses the climate setpoints held on its channels.
const { rules, triggers, items, time } = require('openhab');

const DEPARTURE = '06:55';   // HH:MM, Monday to Friday
const LEAD_MIN = 20;

rules.JSRule({
  name: 'Car preheat before departure',
  triggers: [triggers.GenericCronTrigger('5 * * * * ?')],
  execute: () => {
    if (items.getItem('Car_At_Home').state !== 'ON') return;
    const now = time.toZDT();
    if (now.dayOfWeek().value() >= 6) return;
    const [hh, mm] = DEPARTURE.split(':').map(Number);
    const start = now.withHour(hh).withMinute(mm).withSecond(0).withNano(0).minusMinutes(LEAD_MIN);
    const diffMin = (now.toInstant().toEpochMilli() - start.toInstant().toEpochMilli()) / 60000;
    if (diffMin < 0 || diffMin >= 1) return;
    console.info('Preheat: starting the climate ' + LEAD_MIN + ' min before ' + DEPARTURE);
    items.getItem('Car_Climate').sendCommand('ON');
  }
});
