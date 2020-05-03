#!/usr/bin/env python3
import json
import os

process = json.loads(os.environ['self'])
account = json.loads(os.environ['account'])
vars = json.loads(os.environ['vars'])

sender = vars[-4][process]
receiver = vars[-3][process]
money = vars[-2][process]

account[sender] -= money
account[receiver] += money

print(json.dumps(account))
