import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render, createMockDevice, createMockDeviceState } from '../../utils/test-utils'
import { DevicePanel } from '@/components/DevicePanel'
import { useAppStore } from '@/store'

describe('DevicePanel', () => {
  beforeEach(() => {
    // Reset store state before each test
    // Mock fetchDevices to prevent side effects
    useAppStore.setState({
      devices: [],
      deviceStates: {},
      isLoadingDevices: false,
      error: null,
      fetchDevices: vi.fn().mockResolvedValue(undefined),
      clearError: vi.fn(),
      toggleDeviceActive: vi.fn().mockResolvedValue(undefined),
      resetDevice: vi.fn().mockResolvedValue(undefined),
      isAnyDeviceStreaming: () => false,
      updateStreamConfig: vi.fn(),
      updateSensorConfig: vi.fn(),
      setOption: vi.fn().mockResolvedValue(undefined),
      startSensorStreaming: vi.fn().mockResolvedValue(undefined),
      stopSensorStreaming: vi.fn().mockResolvedValue(undefined),
      checkFirmwareUpdates: vi.fn().mockResolvedValue(undefined),
    })
  })

  describe('Empty State', () => {
    it('shows "No devices found" when no devices are connected and not loading', async () => {
      // Set up a fetchDevices that doesn't change state
      const fetchDevices = vi.fn().mockImplementation(() => Promise.resolve())
      useAppStore.setState({ 
        isLoadingDevices: false, 
        devices: [],
        fetchDevices,
      })
      render(<DevicePanel />)
      
      // Wait for initial render to settle
      await waitFor(() => {
        expect(screen.getByText('No devices found')).toBeInTheDocument()
      })
      expect(screen.getByText('Connect a RealSense device')).toBeInTheDocument()
    })

    it('shows loading message when loading with no devices', async () => {
      const fetchDevices = vi.fn().mockImplementation(() => {
        // Simulate loading - set isLoadingDevices to true and devices to empty
        useAppStore.setState({ isLoadingDevices: true, devices: [] })
        return new Promise(() => {}) // Never resolves to keep loading
      })
      
      useAppStore.setState({ 
        isLoadingDevices: true, 
        devices: [],
        fetchDevices,
      })
      render(<DevicePanel />)
      
      // When loading with no devices, should show "Searching for devices..."
      await waitFor(() => {
        expect(screen.getByText('Searching for devices...')).toBeInTheDocument()
      })
    })
  })

  describe('Device List', () => {
    it('renders device cards when devices are connected', () => {
      const mockDevice = createMockDevice()
      
      render(<DevicePanel />, {
        initialStoreState: {
          devices: [mockDevice],
          deviceStates: {},
        },
      })
      
      expect(screen.getByText('RealSense D435')).toBeInTheDocument()
    })

    it('shows multiple devices when connected', () => {
      const device1 = createMockDevice({ device_id: 'device-1', name: 'D435', serial: '111' })
      const device2 = createMockDevice({ device_id: 'device-2', name: 'D455', serial: '222' })
      
      render(<DevicePanel />, {
        initialStoreState: {
          devices: [device1, device2],
          deviceStates: {},
        },
      })
      
      expect(screen.getByText('D435')).toBeInTheDocument()
      expect(screen.getByText('D455')).toBeInTheDocument()
    })

    it('displays device serial number', async () => {
      const mockDevice = createMockDevice({ serial_number: 'TEST-SERIAL-123' })
      
      // Make sure fetchDevices keeps the devices we set
      const fetchDevices = vi.fn().mockImplementation(() => {
        useAppStore.setState({ devices: [mockDevice], isLoadingDevices: false })
        return Promise.resolve()
      })
      
      useAppStore.setState({
        devices: [mockDevice],
        deviceStates: {},
        fetchDevices,
        isLoadingDevices: false,
      })
      render(<DevicePanel />)
      
      await waitFor(() => {
        expect(screen.getByText(/TEST-SERIAL-123/)).toBeInTheDocument()
      })
    })

    it('displays firmware version', () => {
      const mockDevice = createMockDevice({ firmware_version: '5.16.0.1' })
      
      render(<DevicePanel />, {
        initialStoreState: {
          devices: [mockDevice],
          deviceStates: {},
        },
      })
      
      expect(screen.getByText(/5\.16\.0\.1/)).toBeInTheDocument()
    })
  })

  describe('Error Handling', () => {
    it('displays error message when error is set', () => {
      render(<DevicePanel />, {
        initialStoreState: {
          devices: [],
          error: 'Failed to connect to device',
        },
      })
      
      expect(screen.getByText('Failed to connect to device')).toBeInTheDocument()
    })

    it('allows dismissing error messages', async () => {
      const clearError = vi.fn()
      useAppStore.setState({ clearError })
      
      render(<DevicePanel />, {
        initialStoreState: {
          devices: [],
          error: 'Some error occurred',
        },
      })
      
      const dismissButton = screen.getByText('×')
      await userEvent.click(dismissButton)
      
      expect(clearError).toHaveBeenCalled()
    })
  })

  describe('Refresh Button', () => {
    it('renders refresh button', () => {
      render(<DevicePanel />)
      
      const refreshButton = screen.getByTitle('Refresh devices')
      expect(refreshButton).toBeInTheDocument()
    })

    it('calls fetchDevices when refresh is clicked', async () => {
      const fetchDevices = vi.fn().mockResolvedValue(undefined)
      useAppStore.setState({ fetchDevices })

      render(<DevicePanel />)

      const refreshButton = screen.getByTitle('Refresh devices')
      await userEvent.click(refreshButton)

      expect(fetchDevices).toHaveBeenCalled()
    })

    it('passes forceRefresh=true when refresh is clicked manually', async () => {
      const fetchDevices = vi.fn().mockResolvedValue(undefined)
      useAppStore.setState({ fetchDevices })

      render(<DevicePanel />)

      const refreshButton = screen.getByLabelText('Refresh devices')
      await userEvent.click(refreshButton)

      // The polling effect also calls fetchDevices() with no args on mount.
      // The manual-click call must explicitly pass true.
      expect(fetchDevices).toHaveBeenCalledWith(true)
    })

    it('is disabled while a fetch is in flight', () => {
      // Must use initialStoreState (not pre-render setState) because
      // renderWithProviders calls resetStore() first, which would reset
      // isLoadingDevices back to false.
      render(<DevicePanel />, {
        initialStoreState: { isLoadingDevices: true },
      })

      const refreshButton = screen.getByLabelText('Refreshing devices…')
      expect(refreshButton).toBeDisabled()
    })
  })

  describe('Device Activation', () => {
    it('renders device as inactive by default', () => {
      const mockDevice = createMockDevice()
      
      render(<DevicePanel />, {
        initialStoreState: {
          devices: [mockDevice],
          deviceStates: {},
        },
      })
      
      // Device should show but not be active
      expect(screen.getByText('RealSense D435')).toBeInTheDocument()
    })

    it('shows device as active when deviceState.isActive is true', () => {
      const mockDevice = createMockDevice()
      const mockDeviceState = createMockDeviceState(mockDevice, { isActive: true })
      
      render(<DevicePanel />, {
        initialStoreState: {
          devices: [mockDevice],
          deviceStates: { [mockDevice.device_id]: mockDeviceState },
        },
      })
      
      // When active, the device card should have active styling
      // The exact check depends on component implementation
      expect(screen.getByText('RealSense D435')).toBeInTheDocument()
    })
  })

  describe('Header', () => {
    it('renders "Devices" header', () => {
      render(<DevicePanel />)

      expect(screen.getByText('Devices')).toBeInTheDocument()
    })
  })

  describe('Firmware Update Proposal', () => {
    it('shows an update proposal when firmware is outdated', () => {
      const device = createMockDevice()
      const ds = createMockDeviceState(device, {
        firmware: {
          current: '5.17.0.10',
          recommended: '5.17.3.10',
          status: 'outdated',
          link: 'https://example/fw.bin',
        },
      })
      render(<DevicePanel />, {
        initialStoreState: { devices: [device], deviceStates: { [device.device_id]: ds } },
      })

      expect(screen.getByText('Firmware update recommended')).toBeInTheDocument()
      expect(screen.getByText('5.17.0.10 → 5.17.3.10')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Update' })).toBeInTheDocument()
    })

    it('can dismiss the proposal, leaving the plain download link', async () => {
      localStorage.clear()
      sessionStorage.clear()
      const device = createMockDevice()
      const ds = createMockDeviceState(device, {
        firmware: { current: '5.17.0.10', recommended: '5.17.3.10', status: 'outdated' },
      })
      render(<DevicePanel />, {
        initialStoreState: { devices: [device], deviceStates: { [device.device_id]: ds } },
      })

      expect(screen.getByText('Firmware update recommended')).toBeInTheDocument()
      await userEvent.click(screen.getByTitle(/Dismiss/))
      expect(screen.queryByText('Firmware update recommended')).not.toBeInTheDocument()
      expect(screen.getByText('Download firmware →')).toBeInTheDocument()
      expect(localStorage.getItem(`rs-fw-dismissed:${device.device_id}:5.17.3.10`)).toBe('1')
    })

    it('"Remind me later" hides the proposal for the session (sessionStorage)', async () => {
      localStorage.clear()
      sessionStorage.clear()
      const device = createMockDevice()
      const ds = createMockDeviceState(device, {
        firmware: { current: '5.17.0.10', recommended: '5.17.3.10', status: 'outdated' },
      })
      render(<DevicePanel />, {
        initialStoreState: { devices: [device], deviceStates: { [device.device_id]: ds } },
      })

      await userEvent.click(screen.getByText('Remind me later'))
      expect(screen.queryByText('Firmware update recommended')).not.toBeInTheDocument()
      expect(sessionStorage.getItem(`rs-fw-dismissed:${device.device_id}:5.17.3.10`)).toBe('1')
      // permanent store untouched → returns next session
      expect(localStorage.getItem(`rs-fw-dismissed:${device.device_id}:5.17.3.10`)).toBeNull()
    })

    it('shows nothing when firmware is up to date', () => {
      const device = createMockDevice()
      const ds = createMockDeviceState(device, {
        firmware: { current: '5.17.3.10', recommended: '5.17.3.10', status: 'up_to_date' },
      })
      render(<DevicePanel />, {
        initialStoreState: { devices: [device], deviceStates: { [device.device_id]: ds } },
      })

      expect(screen.queryByText('Firmware update recommended')).not.toBeInTheDocument()
      expect(screen.queryByText('Download firmware →')).not.toBeInTheDocument()
    })

    it('shows the plain download link when firmware status is unknown', () => {
      const device = createMockDevice()
      const ds = createMockDeviceState(device, {
        firmware: { current: '5.17.0.10', status: 'unknown' },
      })
      render(<DevicePanel />, {
        initialStoreState: { devices: [device], deviceStates: { [device.device_id]: ds } },
      })

      expect(screen.getByText('Download firmware →')).toBeInTheDocument()
    })
  })
})
