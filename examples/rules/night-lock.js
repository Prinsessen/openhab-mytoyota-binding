// 23:00: lock if parked at home and unlocked. 23:03: report, and say why if the car refused
// (the reason is on notifications#latest, e.g. "a keyfob was detected in your vehicle").
const { rules, triggers, items, actions } = require('openhab');

rules.JSRule({
  name: 'Car night lock',
  triggers: [triggers.GenericCronTrigger('0 0 23 * * ?')],
  execute: () => {
    if (items.getItem('Car_At_Home').state !== 'ON') return;
    if (items.getItem('Car_Locked').state === 'ON') return;
    items.getItem('Car_Lock').sendCommand('ON');
  }
});

rules.JSRule({
  name: 'Car night check',
  triggers: [triggers.GenericCronTrigger('0 3 23 * * ?')],
  execute: () => {
    if (items.getItem('Car_At_Home').state !== 'ON') return;
    if (items.getItem('Car_Locked').state === 'ON') return;
    const mail = actions.Things.getActions('mail', 'mail:smtp:home');
    if (!mail) return;
    mail.sendMail('me@example.com', 'Car still unlocked at 23:03',
      'Last command: ' + items.getItem('Car_LastCmd').state + '. Car says: ' + items.getItem('Car_Says').state);
  }
});
