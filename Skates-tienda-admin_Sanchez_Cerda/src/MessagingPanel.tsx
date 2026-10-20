import { useEffect, useState } from "react";
import {
  cambiarPausaConsumidor,
  obtenerResumenMensajeria,
  type MessagingOverview,
  type MessagingQueueStatus,
} from "./api";

const queueLabels: Record<string, string> = {
  orders: "Órdenes",
  inventory: "Inventario",
  notifications: "Notificaciones",
};

export default function MessagingPanel() {
  const [overview, setOverview] = useState<MessagingOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busyQueue, setBusyQueue] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = async (showLoading = false) => {
    if (showLoading) setRefreshing(true);
    try {
      const next = await obtenerResumenMensajeria();
      setOverview(next);
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No se pudo consultar SNS/SQS.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const next = await obtenerResumenMensajeria();
        if (active) {
          setOverview(next);
          setError(null);
        }
      } catch (cause) {
        if (active) {
          setError(cause instanceof Error ? cause.message : "No se pudo consultar SNS/SQS.");
        }
      } finally {
        if (active) setLoading(false);
      }
    };

    void load();
    const interval = window.setInterval(() => void load(), 15000);
    return () => {
      active = false;
      window.clearInterval(interval);
    };
  }, []);

  const setPaused = async (queue: MessagingQueueStatus, paused: boolean) => {
    setBusyQueue(queue.name);
    setError(null);
    try {
      await cambiarPausaConsumidor(queue.name, paused);
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No se pudo cambiar el estado del consumidor.");
    } finally {
      setBusyQueue(null);
    }
  };

  if (loading) {
    return <p className="messaging-message">Consultando estado de SNS y SQS...</p>;
  }

  return (
    <div className="messaging-panel">
      <div className="messaging-heading">
        <div>
          <h3>Monitoreo de mensajería</h3>
          <p>Estado de las colas, consumidores y mensajes en DLQ.</p>
        </div>
        <button type="button" onClick={() => void refresh(true)} disabled={refreshing}>
          {refreshing ? "Actualizando..." : "Actualizar"}
        </button>
      </div>

      {error && <p className="messaging-error" role="alert">{error}</p>}
      {overview ? (
        <>
          <div className="messaging-topic">
            <span>Topic SNS</span>
            <code>{overview.topicArn || "ARN no configurado"}</code>
          </div>
          <div className="messaging-queues">
            {overview.queues.map((queue) => (
              <article className="messaging-queue-card" key={queue.name}>
                <header>
                  <div>
                    <small>Cola SQS</small>
                    <h4>{queueLabels[queue.name] ?? queue.name}</h4>
                  </div>
                  <span className={`messaging-status ${queue.paused ? "is-paused" : "is-running"}`}>
                    {queue.paused ? "Pausado" : "Activo"}
                  </span>
                </header>

                <dl>
                  <div><dt>Disponibles</dt><dd>{queue.visibleMessages}</dd></div>
                  <div><dt>En proceso</dt><dd>{queue.inFlightMessages}</dd></div>
                  <div className={queue.deadLetterMessages > 0 ? "has-dlq-messages" : ""}>
                    <dt>Mensajes en DLQ</dt><dd>{queue.deadLetterMessages}</dd>
                  </div>
                </dl>

                <button
                  type="button"
                  disabled={busyQueue === queue.name}
                  onClick={() => void setPaused(queue, !queue.paused)}
                >
                  {busyQueue === queue.name ? "Guardando..." : queue.paused ? "Reanudar consumidor" : "Pausar consumidor"}
                </button>
              </article>
            ))}
          </div>
          <section className="messaging-events">
            <h3>Eventos procesados recientemente</h3>
            {overview.recentEvents.length === 0 ? (
              <p className="messaging-message">Todavía no hay eventos procesados registrados en la base de datos.</p>
            ) : (
              <div className="messaging-event-list">
                {overview.recentEvents.map((event) => (
                  <article className="messaging-event" key={`${event.consumerName}:${event.eventId}`}>
                    <div>
                      <strong>{event.eventType}</strong>
                      <span>{queueLabels[event.consumerName] ?? event.consumerName}</span>
                    </div>
                    <code>{event.eventId}</code>
                    <time dateTime={event.processedAt}>
                      {new Date(event.processedAt).toLocaleString("es-CL")}
                    </time>
                  </article>
                ))}
              </div>
            )}
          </section>
          <p className="messaging-note">
            Actualización automática cada 15 segundos. Los eventos recientes vienen de la base de datos; esta vista no lee ni retira cuerpos de mensajes de SQS.
          </p>
        </>
      ) : (
        <p className="messaging-message">No se recibió información de mensajería del backend.</p>
      )}
    </div>
  );
}
