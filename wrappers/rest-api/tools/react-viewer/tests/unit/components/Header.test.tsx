import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../utils/test-utils'
import { Header } from '@/components/Header'

describe('Header', () => {
  it('renders the application logo', () => {
    render(<Header />)
    const logo = screen.getByAltText('RealSense')
    expect(logo).toBeInTheDocument()
  })

  it('shows 2D and 3D view buttons when devices are active', () => {
    render(<Header />, {
      initialStoreState: {
        deviceStates: {
          '123': {
            isActive: true,
            device: { device_id: '123', name: 'Test Device' },
          } as any,
        },
      },
    })

    expect(screen.getByText(/2D View/i)).toBeInTheDocument()
    expect(screen.getByText(/3D View/i)).toBeInTheDocument()
  })

  it('3D view button is enabled', () => {
    render(<Header />, {
      initialStoreState: {
        deviceStates: {
          '123': {
            isActive: true,
            device: { device_id: '123', name: 'Test Device' },
          } as any,
        },
      },
    })

    const button = screen.getByText(/3D View/i).closest('button')
    expect(button).not.toBeDisabled()
  })

  it('does not show view toggle when no active devices', () => {
    render(<Header />, {
      initialStoreState: {
        deviceStates: {},
      },
    })

    expect(screen.queryByText(/2D View/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/3D View/i)).not.toBeInTheDocument()
  })

  it('shows SDK and viewer versions in the About dialog', async () => {
    render(<Header />)
    await userEvent.click(screen.getByTitle('About'))

    expect(screen.getByText('librealsense SDK')).toBeInTheDocument()
    expect(screen.getByText('Viewer')).toBeInTheDocument()
    // Viewer version is injected from rs.h as major.minor.patch.build
    expect(screen.getByText(/^\d+\.\d+\.\d+\.\d+$/)).toBeInTheDocument()
  })
})
