-- Una seña sin reserva que Pagos no acepta devolver deja de pedirse (DEF-29): failed_at dice cuándo
-- Pagos la rechazó. Sin esto el barrido la pedía cada 15 s para siempre, con un ERROR cada vez.
-- Las reservas no necesitan migración: deposit_status es texto y suma el valor REFUND_FAILED.

ALTER TABLE pending_deposit_refunds ADD COLUMN failed_at TIMESTAMP WITH TIME ZONE;
