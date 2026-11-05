# FE-08 GHTK fulfilment decision

## Problem

A GHTK tracking number proves that the provider accepted a shipment-creation
request. It does not prove that the prepared parcel has physically left the
Mland branch. Treating tracking-number creation as delivery would remove the
only custody checkpoint owned by Staff.

## Options reviewed

1. Skip Staff handoff: tracking number -> preparing -> delivered. This is
   simple but does not record custody of the physical parcel.
2. **Approved flow:** create shipment and store tracking number automatically;
   Staff prepares and records the physical GHTK handoff; GHTK then owns transit
   and sends the delivered status.
3. Require Staff to create the shipment after packing. This adds work without
   value because the system already owns automatic shipment creation.

## Approved flow

```text
Verified payment
-> system creates GHTK shipment and receives tracking number
-> Staff prepares parcel
-> Staff records physical GHTK handoff
-> GHTK updates in-transit / delivered status
-> order completes on delivered confirmation
```

The Member sees normalized carrier status only. The Staff handoff record stores
the operational actor and actual handoff time. A provider callback, not Staff,
sets the final delivered state.

## Prototype boundary

The review UI may simulate the provider tracking-number response and delivered
callback. It must not call a GHTK order-creation, cancellation, label, or
webhook endpoint.
