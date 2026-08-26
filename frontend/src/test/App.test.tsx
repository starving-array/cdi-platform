import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { App } from '../App';
import { DevSecurityProvider, useDevSecurity } from '../context/DevSecurityContext';

describe('Frontend Shell & Dev Security Context', () => {
  it('renders application shell and default feed page', () => {
    render(<App />);

    expect(screen.getByText('CDI Platform')).toBeInTheDocument();
    expect(screen.getAllByText('Change Decision Feed').length).toBeGreaterThan(0);
  });

  it('switches navigation between Change Feed and Evidence Search', () => {
    render(<App />);

    const searchBtn = screen.getByRole('button', { name: /Evidence Search/i });
    fireEvent.click(searchBtn);

    expect(screen.getByTestId('search-placeholder')).toBeInTheDocument();

    const feedBtn = screen.getByRole('button', { name: /Change Decision Feed/i });
    fireEvent.click(feedBtn);

    expect(screen.getAllByText('Change Decision Feed').length).toBeGreaterThan(0);
  });

  it('allows updating role and tenant in dev security context', () => {
    const TestConsumer = () => {
      const { tenantId, actorRole, setActorRole, setTenantId } = useDevSecurity();
      return (
        <div>
          <span data-testid="tenant-val">{tenantId}</span>
          <span data-testid="role-val">{actorRole}</span>
          <button onClick={() => setTenantId('custom-tenant-uuid')}>Set Tenant</button>
          <button onClick={() => setActorRole('TENANT_ADMIN')}>Set Admin</button>
        </div>
      );
    };

    render(
      <DevSecurityProvider>
        <TestConsumer />
      </DevSecurityProvider>
    );

    expect(screen.getByTestId('role-val')).toHaveTextContent('ENGINEER');

    fireEvent.click(screen.getByText('Set Admin'));
    expect(screen.getByTestId('role-val')).toHaveTextContent('TENANT_ADMIN');

    fireEvent.click(screen.getByText('Set Tenant'));
    expect(screen.getByTestId('tenant-val')).toHaveTextContent('custom-tenant-uuid');
  });
});