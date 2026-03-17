import React from 'react';
import lodash from 'lodash';
import { debounce } from 'lodash';
import * as rxjs from 'rxjs';

const moment = require('moment');

function test() {
  const component = <div>Hello</div>;
  console.log(lodash, debounce, rxjs, moment);
}

export default test;
