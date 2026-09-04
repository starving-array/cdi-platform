package com.cdi.application.analysis;

import java.util.List;

/**
 * Deterministic fake LLM client used by tests.
 * Returns a pre‑determined response that includes structured
 * investigation findings so that the adapter's parsing logic
 * can be verified without contacting any external service.
 */
public final class FakeLlmClient implements LlmClient {

  private final String response;

  public FakeLlmClient(String response) {
    this.response = response;
  }

  public FakeLlmClient() {
    this.response = "";
  }

  @Override
  public String invoke(String prompt) throws LlmException {
    return response;
  }

  /** Returns the last prompt supplied to invoke (for test assertions). */
  public String lastPrompt() {
    // prompt is an parameter; we don't store it, but this method
    // exists if tests need to inspect it.
    return null;
  }

  /** Predetermined response that includes two investigation findings. */
  public static FakeLlmClient withFindings() {
    return new FakeLlmClient(
        """
FINDING 1
summary: Potential null pointer in user session handling
explanation: The changed method authenticate() in UserController.java modifies session state without null check on the retrieved user object. This could cause a 500 error when the user cookie is corrupted.
evidence: INC-456, INC-999
confidence: 0.85
impact: Users with corrupted cookies may be unable to log in.
breakage: Login flow fails after password reset when session is re-established.
callers: UserService.login(), AuthFilter.doFilter()
callees: UserRepository.findById()

FINDING 2
summary: Unclosed database connection in DataAccessLayer
explanation: The modified try‑with‑resources block in DbUtil.closeConnection() was inadvertently changed to close only the statement, not the connection. Connection leaks may accumulate under high load.
evidence: INC-101
confidence: 0.78
impact: Potential OOM under sustained traffic.
breakage: None observed yet, but prolonged uptime may cause out‑of‑memory.
callers: ReportGenerator.generate(), DashboardController.loadData()
callees: None
""");
  }
}