import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { expect, it } from "vitest";
import { AuthProvider, RequireAuth } from "../features/auth/AuthProvider";
it("protects private routes before authentication", async () => {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={["/private"]}>
        <AuthProvider>
          <Routes>
            <Route element={<RequireAuth />}>
              <Route path="/private" element={<p>Private wallet</p>} />
            </Route>
            <Route path="/login" element={<p>Please sign in</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  expect(await screen.findByText("Please sign in")).toBeVisible();
  expect(screen.queryByText("Private wallet")).not.toBeInTheDocument();
});
