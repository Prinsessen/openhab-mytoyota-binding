// At 20:00 and 22:00: parked at home, below 40 % and not charging -> a mail.
// Uses the openHAB Mail binding; put your mail thing id and address in.
const { rules, triggers, items, actions } = require('openhab');

rules.JSRule({
  name: 'Car plug-in reminder',
  triggers: [triggers.GenericCronTrigger('0 0 20 * * ?'), triggers.GenericCronTrigger('0 0 22 * * ?')],
  execute: () => {
    if (items.getItem('Car_At_Home').state !== 'ON') return;
    const soc = parseFloat(items.getItem('Car_SoC').state);
    if (isNaN(soc) || soc >= 40) return;
    if (items.getItem('Car_Charging').state === 'ON') return;
    const mail = actions.Things.getActions('mail', 'mail:smtp:home');
    if (!mail) return;
    mail.sendMail('me@example.com', 'Car at ' + Math.round(soc) + ' % and not plugged in',
      'Range ' + items.getItem('Car_Range').state + '. Battery reported ' + items.getItem('Car_Battery_At').state + '.');
  }
});
