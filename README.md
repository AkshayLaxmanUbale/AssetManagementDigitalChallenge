## Asset Management Digital Challenge

#### Endpoint :

    http://<base_address>:<port>/v1/transaction
    METHOD: POST
    PAYLOAD : 
    {
        "from": "from_account_id",
        "to": "to_account_id",
        "amount": "transaction_amount"
    }
    
#### Implementation Details
    
    - Created a separate controller TransactionController for the new endpoint.

    - Added TransactionService for implementing the business logic of transfer functionality.
        - Checks if both accounts are present or not.
        - After validation does the transfer of amount from one account to another.
        - On Completing the transfer send notification to both accounts about the operation.
    
    - Implemented two new methods in Account class i.e. debit and credit.
        - These two methods are thread safe.
        - Implemented thread-safety using ReentrantReadWriteLock.
        - These methods first acquire the write lock or wait until the write lock is available.
        - After acquiring the write lock, we update the balance.

#### Other Ways
    
    - We can also achieve the same functionality using synchronized method or block implementation.



