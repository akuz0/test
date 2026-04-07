import org.testng.annotations.Test;
import org.testng.annotations.Parameters;
import org.testng.annotations.Optional;
import com.consol.citrus.annotations.CitrusTest;
import com.consol.citrus.annotations.CitrusResource;
import com.consol.citrus.context.TestContext;
import io.qameta.allure.TmsLink;
import io.qameta.allure.Story;

import javax.sql.DataSource;

public class AccountManagementTest {

    /*
    
    com.consol.citrus.exceptions.TestCaseFailedException: Validation failed for column: 'account_status' found value: '0' expected value: 3
    */

    @Story("Account Closure Operations")
    @TmsLink("ACC-2110")
    @Test(description = "Close customer account with non-zero balance without specifying reason")
    @Parameters({"executionContext"})
    @CitrusTest
    public void testCloseAccountWithBalance(@Optional @CitrusResource TestContext executionContext) {

        prepareAccountWithBalance(executionContext, "1000.00");

        applyBehavior(new AccountClosureAction(executionContext));

        verifyBindingStatus(executionContext, executionContext.getVariable("sourceBindingId"), 3);

        verifyAccountState(executionContext, 3);
    }

    private void prepareAccountWithBalance(TestContext executionContext, String balanceAmount) {
        // Implementation for preparing account with specified balance
        executionContext.setVariable("accountBalance", balanceAmount);
    }

    private void verifyBindingStatus(TestContext executionContext, String bindingId, int expectedStatus) {
        // Implementation for verifying binding status
        executionContext.setVariable("bindingStatus", expectedStatus);
    }

    private void verifyAccountState(TestContext executionContext, int expectedState) {
        DataSource financialDataSource = (DataSource) executionContext.getApplicationContext().getBean("financialDataSource");
        if (!executionContext.getVariables().containsKey("accountIdentifier")) {
            // Retrieve account number and card number association for potential data restoration
            query(action -> action
                    .dataSource(financialDataSource)
                    .statement("SELECT \n" +
                            "account_number, card_identifier\n" +
                            "FROM account_table A\n" +
                            "LEFT JOIN card_account_table cat ON A.account_number = cat.account_no\n" +
                            "LEFT JOIN card_reference_table ct ON cat.card_identifier = ct.card_ref_no\n" +
                            "WHERE card_type in ('EM','EF') AND\n" +
                            "ct.mobile_phone IN ('" + 7 + executionContext.getVariable("userPhoneNumber") + "')")
                    .extract("account_number", "accountIdentifier")
                    .extract("card_identifier", "cardIdentifier")
            );
        }

        query(action -> action
                .dataSource(financialDataSource)
                .statement("SELECT * FROM account_table WHERE account_number='" + executionContext.getVariable("accountIdentifier") + "'")
                .validate("account_status", String.valueOf(expectedState)));
    }

    private void query(QueryActionBuilder action) {
        // Query implementation placeholder
        action.build();
    }
}

class QueryActionBuilder {
    private DataSource dataSource;
    private String statement;
    
    public QueryActionBuilder dataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        return this;
    }
    
    public QueryActionBuilder statement(String statement) {
        this.statement = statement;
        return this;
    }
    
    public QueryActionBuilder extract(String column, String variableName) {
        // Extract implementation
        return this;
    }
    
    public QueryActionBuilder validate(String column, String expectedValue) {
        // Validate implementation
        return this;
    }
    
    public void build() {
        // Build implementation
    }
}
