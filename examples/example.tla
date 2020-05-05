-------------------------------- MODULE example --------------------------------

EXTENDS Integers, Sequences, TLC

Processes == {"t1", "t2"}

(*--algorithm transaction {
    variables
    c1 = "c1",
    c2 = "c2",
    account = [c \in {"c1", "c2"} |-> 10],
    receiver_new_amount = [self \in Processes |-> 0],
    sender_new_amount = [self \in Processes |-> 0],
    sender = [self \in Processes |-> c1],
    receiver = [self \in Processes |-> c2],
    money \in [Processes -> 1..5];
    define {
        ConstantBalance ==
        account["c1"] + account["c2"] = 20
        TransferMoney(self, acc, vars) ==
        [account EXCEPT
         ![sender[self]] = account[sender[self]] - money[self],
         ![receiver[self]] = account[receiver[self]] + money[self]]
    }
    fair process (tx \in Processes)
    {
        ADAPT: skip;
        TRANSFER_MONEY: account := TransferMoney(self, account, vars);
    }
}*)

\* BEGIN TRANSLATION
VARIABLES c1, c2, account, receiver_new_amount, sender_new_amount, sender, 
          receiver, money, pc

(* define statement *)
ConstantBalance ==
account["c1"] + account["c2"] = 20

TransferMoney(self, acc, vars) ==
[account EXCEPT
 ![sender[self]] = account[sender[self]] - money[self],
 ![receiver[self]] = account[receiver[self]] + money[self]]


vars == << c1, c2, account, receiver_new_amount, sender_new_amount, sender, 
           receiver, money, pc >>

ProcSet == (Processes)

Init == (* Global variables *)
        /\ c1 = "c1"
        /\ c2 = "c2"
        /\ account = [c \in {"c1", "c2"} |-> 10]
        /\ receiver_new_amount = [self \in Processes |-> 0]
        /\ sender_new_amount = [self \in Processes |-> 0]
        /\ sender = [self \in Processes |-> c1]
        /\ receiver = [self \in Processes |-> c2]
        /\ money \in [Processes -> 1..5]
        /\ pc = [self \in ProcSet |-> "ADAPT"]

ADAPT(self) == /\ pc[self] = "ADAPT"
               /\ TRUE
               /\ pc' = [pc EXCEPT ![self] = "TRANSFER_MONEY"]
               /\ UNCHANGED << c1, c2, account, receiver_new_amount, 
                               sender_new_amount, sender, receiver, money >>

TRANSFER_MONEY(self) == /\ pc[self] = "TRANSFER_MONEY"
                        /\ account' = TransferMoney(self, account, vars)
                        /\ pc' = [pc EXCEPT ![self] = "Done"]
                        /\ UNCHANGED << c1, c2, receiver_new_amount, 
                                        sender_new_amount, sender, receiver, 
                                        money >>

tx(self) == ADAPT(self) \/ TRANSFER_MONEY(self)

(* Allow infinite stuttering to prevent deadlock on termination. *)
Terminating == /\ \A self \in ProcSet: pc[self] = "Done"
               /\ UNCHANGED vars

Next == (\E self \in Processes: tx(self))
           \/ Terminating

Spec == /\ Init /\ [][Next]_vars
        /\ \A self \in Processes : WF_vars(tx(self))

Termination == <>(\A self \in ProcSet: pc[self] = "Done")

\* END TRANSLATION

CorrectTransfer ==
  \A t \in {"t1"}:
     <>(/\ pc[t] = "Done"
        /\ account["c1"] = 10 - money["t1"] - money["t2"]
        /\ account["c2"] = 10 + money["t1"] + money["t2"])

=============================================================================
