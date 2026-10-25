'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {test} = require('node:test');

const root = path.resolve(__dirname, '../../..');
const source = name => fs.readFileSync(path.join(root, 'src/main/webapp/assets/js/refund', name), 'utf8');
const core = fs.readFileSync(path.join(root, 'src/main/webapp/assets/js/core/tc-core.js'), 'utf8');
const metas = {'context-path': '/tc', 'csrf-token': 'csrf-token'};
const metaTags = selector => {
  const name = /^meta\[name="([^"]+)"\]$/.exec(selector)?.[1];
  return name ? [{getAttribute: () => metas[name]}] : null;
};

function run(script, globals) {
  const context = vm.createContext(globals);
  vm.runInContext(core, context);
  vm.runInContext(script, context);
}
const flush = () => new Promise(resolve => setImmediate(resolve));

class Element {
  constructor(tag = 'div') {
    this.tag = tag;
    this.dataset = {};
    this.children = [];
    this.listeners = {};
    this.value = '';
    this.textContent = '';
    this.disabled = false;
    this.checked = false;
    this.open = false;
    this.options = [];
  }
  append(...children) { this.children.push(...children); }
  replaceChildren(...children) { this.children = children; }
  addEventListener(type, handler) { (this.listeners[type] ||= []).push(handler); }
  dispatchEvent(event) {
    for (const handler of this.listeners[event.type] || []) handler(event);
    this['on' + event.type]?.(event);
    return true;
  }
  querySelector(selector) {
    const id = /^input\[data-id="([^"]+)"\]$/.exec(selector)?.[1];
    return this.descendants().find(element => element.tag === 'input' && element.dataset.id === id) || null;
  }
  descendants() { return this.children.flatMap(child => [child, ...child.descendants()]); }
  showModal() { this.open = true; }
  close() { this.open = false; }
  async trigger(type) {
    const event = {type, preventDefault() {}};
    for (const handler of this.listeners[type] || []) await handler(event);
    await this['on' + type]?.(event);
    await flush();
  }
}

function harness(script, ids, fetch, dataset = {}) {
  const elements = Object.fromEntries(ids.map(id => [id, new Element()]));
  Object.assign(elements.app.dataset, dataset);
  const redirects = [];
  let generated = 0;
  run(script, {
    document: {
      querySelector: selector => elements[selector.replace(/^#/, '')],
      querySelectorAll: selector => metaTags(selector) || elements.buttons || [],
      createElement: tag => new Element(tag)
    },
    fetch,
    crypto: {randomUUID: () => 'key-' + ++generated},
    location: {assign: url => redirects.push(url)},
    Event: class { constructor(type) { this.type = type; } }
  });
  return {elements, redirects};
}
const reply = data => ({ok: true, json: async () => ({data})});

async function checkRequestForm(script) {
  const active = [
    {ticketId: 'a', seatLabel: 'A1', paidAmount: '9007199254740993', isRefundable: true},
    {ticketId: 'b', seatLabel: 'A2', paidAmount: '7', isRefundable: true},
    {ticketId: 'c', seatLabel: 'A3', paidAmount: '10', isRefundable: false, unrefundableReason: '<img src=x>'}
  ];
  const calls = [];
  let failFirst = true;
  const {elements: e, redirects} = harness(script,
    ['app', 'order', 'tickets', 'reason', 'message', 'submit', 'confirm', 'total', 'form', 'confirmation', 'cancel', 'send'],
    async (url, options) => {
      calls.push({url, options});
      if (options.method !== 'POST') return reply(active);
      if (failFirst) { failFirst = false; throw new TypeError('Connection lost'); }
      return reply({message: 'Đã tiếp nhận.', totalRefundAmount: '9007199254741000'});
    }, {order: ''});

  e.order.value = 'order-1';
  await e.order.trigger('change');
  assert.equal(calls[0].url, '/tc/api/orders/order-1/refundable-tickets');
  assert.equal(e.tickets.children.length, 3);
  assert.equal(e.tickets.querySelector('input[data-id="c"]').disabled, true);
  assert.equal(e.tickets.children[2].children[1].textContent, '<img src=x>');
  assert.equal(e.submit.disabled, true);
  for (const id of ['a', 'b']) {
    const input = e.tickets.querySelector('input[data-id="' + id + '"]');
    input.checked = true;
    await input.trigger('change');
  }
  assert.equal(e.total.textContent, 'Số tiền dự kiến: 9.007.199.254.741.000 ₫');
  assert.equal(e.submit.disabled, false);

  e.reason.value = 'short';
  await e.form.trigger('submit');
  assert.equal(e.confirm.open, false);
  assert.equal(e.message.textContent, 'Lý do cần ít nhất 10 ký tự.');
  e.reason.value = '  Không thể tham dự sự kiện.  ';
  await e.form.trigger('submit');
  assert.equal(e.confirm.open, true);
  assert.match(e.confirmation.textContent, /^Bạn chọn 2 vé/);
  await e.send.trigger('click');
  const first = calls.find(call => call.options.method === 'POST');
  assert.deepEqual(JSON.parse(first.options.body), {
    orderId: 'order-1', ticketIds: ['a', 'b'], reason: 'Không thể tham dự sự kiện.'
  });
  assert.equal(first.options.headers['X-CSRF-Token'], 'csrf-token');
  assert.equal(first.options.credentials, 'same-origin');
  assert.match(e.message.textContent, /Bạn có thể bấm gửi lại cùng yêu cầu/);
  assert.equal(e.order.disabled, false);
  assert.equal(e.reason.disabled, false);

  await e.form.trigger('submit');
  await e.send.trigger('click');
  const posts = calls.filter(call => call.options.method === 'POST');
  assert.equal(posts.length, 2);
  assert.equal(posts[0].options.headers['Idempotency-Key'], posts[1].options.headers['Idempotency-Key']);
  assert.equal(posts[0].options.body, posts[1].options.body);
  assert.deepEqual(redirects, ['/tc/my-refunds']);
  assert.equal(e.reason.disabled, false);
}

async function checkStaleOrderResponse(script) {
  let resolveFirst;
  const {elements: e} = harness(script,
    ['app', 'order', 'tickets', 'reason', 'message', 'submit', 'confirm', 'total', 'form', 'confirmation', 'cancel', 'send'],
    url => url.includes('old-order') ? new Promise(resolve => { resolveFirst = resolve; })
      : Promise.resolve(reply([{ticketId: 'new', seatLabel: 'B1', paidAmount: '1', isRefundable: true}])),
    {order: ''});
  e.order.value = 'old-order';
  const first = e.order.trigger('change');
  e.order.value = 'new-order';
  await e.order.trigger('change');
  resolveFirst(reply([{ticketId: 'old', seatLabel: 'A1', paidAmount: '2', isRefundable: true}]));
  await first;
  assert.ok(e.tickets.querySelector('input[data-id="new"]'));
  assert.equal(e.tickets.querySelector('input[data-id="old"]'), null);
}

async function checkHistory(script) {
  const button = new Element('button');
  button.dataset.id = 'refund-1';
  const elements = {dialog: new Element('dialog'), details: new Element(), close: new Element()};
  const calls = [];
  run(script, {
    document: {
      querySelector: selector => elements[selector.replace(/^#/, '')],
      querySelectorAll: selector => metaTags(selector) || [button]
    },
    fetch: async (url, options) => {
      calls.push({url, options});
      return reply({refund: {status: 'REJECTED', rejectionReason: '<img src=x>'},
        tickets: [{seatLabel: 'A1', status: 'ACTIVE'}], attempts: []});
    }
  });
  await button.trigger('click');
  assert.equal(calls[0].url, '/tc/api/refund-requests/refund-1');
  assert.equal(calls[0].options.credentials, 'same-origin');
  assert.equal(elements.dialog.open, true);
  assert.equal(elements.details.textContent,
    'Trạng thái: Bị từ chối\nLý do từ chối: <img src=x>\n\nVé:\nA1 · Có hiệu lực\n\nLịch sử chuyển tiền:\nChưa có lần chuyển tiền.');
  assert.equal(elements.details.children.length, 0);
  await elements.close.trigger('click');
  assert.equal(elements.dialog.open, false);
}

test('buyer refund selection, exact totals, confirmation and same-key retry', async () => {
  await checkRequestForm(source('request-form.js'));
});

test('buyer refund keeps the newest order response', async () => {
  await checkStaleOrderResponse(source('request-form.js'));
});

test('buyer refund history opens safely and preserves empty-attempt text', async () => {
  await checkHistory(source('my-refunds.js'));
});