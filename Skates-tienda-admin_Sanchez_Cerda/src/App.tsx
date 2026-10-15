import { useEffect, useMemo, useState } from 'react'
import { obtenerCatalogo, agregarSkate, actualizarSkate, eliminarSkate, type Skate } from './api'
import { cerrarSesion, iniciarSesion } from './auth'
import './App.css'

type Pedido = {
  id: number
  cliente: string
  producto: string
  fecha: string
  estado: 'Pendiente' | 'Enviado' | 'Entregado'
  cantidad: number
  total: number
}

const pedidosDemoIniciales: Pedido[] = [
  { id: 101, cliente: 'Ana García', producto: 'Street / DC', fecha: '2026-09-17', estado: 'Enviado', cantidad: 2, total: 240000 },
  { id: 102, cliente: 'Luis Pérez', producto: 'Longboard / Loaded', fecha: '2026-09-16', estado: 'Pendiente', cantidad: 1, total: 180000 },
  { id: 103, cliente: 'Camila Soto', producto: 'Downhill / Rayne', fecha: '2026-09-15', estado: 'Entregado', cantidad: 3, total: 420000 },
]

export default function App() {
  const [activeTab, setActiveTab] = useState<'products' | 'orders'>('products')
  const [skates, setSkates] = useState<Skate[]>([])
  const [error, setError] = useState<string | null>(null)
  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
  const [autenticado, setAutenticado] = useState(() => Boolean(localStorage.getItem('skates-token')))
  const [errorLogin, setErrorLogin] = useState<string | null>(null)

  // Estados para modales y formularios de productos
  const [modalAbierto, setModalAbierto] = useState(false)
  const [modoEdicion, setModoEdicion] = useState(false)
  const [productoActualId, setProductoActualId] = useState<number | null>(null)
  const [modelo, setModelo] = useState('Street')
  const [marca, setMarca] = useState('')
  const [medida, setMedida] = useState<string>('')
  const [wheelbase, setWheelbase] = useState<string>('')
  const [stock, setStock] = useState<string>('10')

  // Estado para órdenes y pedidos
  const [pedidos, setPedidos] = useState<Pedido[]>(pedidosDemoIniciales)

  const cargarInventario = () => {
    obtenerCatalogo()
      .then((data) => {
        setSkates(data)
        setError(null)
      })
      .catch((err) => setError(err.message || 'No se pudo cargar el inventario.'))
  }

  useEffect(() => {
    if (!autenticado) return
    cargarInventario()
  }, [autenticado])

  const resumenStock = useMemo(() => {
    const totalStock = skates.reduce((sum, skate) => sum + (skate.stock ?? 0), 0)
    const modelos = new Set(skates.map((skate) => skate.modelo)).size
    const bajoStock = skates.filter((skate) => (skate.stock ?? 0) <= 5).length
    return { totalStock, modelos, bajoStock }
  }, [skates])

  const abrirModalCrear = () => {
    setModoEdicion(false)
    setProductoActualId(null)
    setModelo('Street')
    setMarca('')
    setMedida('')
    setWheelbase('')
    setStock('10')
    setModalAbierto(true)
  }

  const abrirModalEditar = (skate: Skate) => {
    setModoEdicion(true)
    setProductoActualId(skate.id)
    setModelo(skate.modelo)
    setMarca(skate.marca)
    setMedida(skate.medida !== null ? String(skate.medida) : '')
    setWheelbase(skate.wheelbase !== null ? String(skate.wheelbase) : '')
    setStock(String(skate.stock))
    setModalAbierto(true)
  }

  const guardarProducto = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      const payload = {
        modelo,
        marca,
        medida: medida ? Number(medida) : null,
        wheelbase: wheelbase ? Number(wheelbase) : null,
        stock: Number(stock),
      }

      if (modoEdicion && productoActualId !== null) {
        await actualizarSkate(productoActualId, payload)
      } else {
        await agregarSkate(payload)
      }

      setModalAbierto(false)
      cargarInventario()
    } catch (err: any) {
      alert(err.message || 'Error al guardar el producto en el backend')
    }
  }

  const eliminarProducto = async (id: number) => {
    if (!confirm('¿Estás seguro de eliminar este producto del inventario?')) return
    try {
      await eliminarSkate(id)
      cargarInventario()
    } catch (err: any) {
      alert(err.message || 'No se pudo eliminar el producto')
    }
  }

  const cambiarEstadoPedido = (id: number, nuevoEstado: 'Pendiente' | 'Enviado' | 'Entregado') => {
    setPedidos(pedidos.map(p => p.id === id ? { ...p, estado: nuevoEstado } : p))
  }

  if (!autenticado) {
    return (
      <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', backgroundColor: '#0f172a', color: '#f8fafc' }}>
        <form 
          onSubmit={(event) => { 
            event.preventDefault(); 
            iniciarSesion(usuario, clave)
              .then(() => setAutenticado(true))
              .catch((loginError: Error) => setErrorLogin(loginError.message)); 
          }} 
          style={{ width: 'min(400px, 90vw)', padding: '36px', backgroundColor: '#1e293b', borderRadius: '14px', border: '1px solid #334155', boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.4)' }}
        >
          <p style={{ color: '#f97316', fontWeight: 700, fontSize: '12px', letterSpacing: '1px', marginBottom: '8px' }}>SKATES ADMIN LOCAL</p>
          <h1 style={{ fontSize: '24px', marginBottom: '24px', color: '#f8fafc' }}>Acceso de administrador</h1>
          
          <div style={{ marginBottom: '16px' }}>
            <input 
              value={usuario} 
              onChange={(event) => setUsuario(event.target.value)} 
              placeholder='Usuario' 
              style={{ display: 'block', width: '100%', padding: '12px 14px', boxSizing: 'border-box', backgroundColor: '#0f172a', border: '1px solid #334155', borderRadius: '8px', color: '#f8fafc', fontSize: '14px', outline: 'none' }} 
            />
          </div>

          <div style={{ marginBottom: '20px' }}>
            <input 
              type='password' 
              value={clave} 
              onChange={(event) => setClave(event.target.value)} 
              placeholder='Contraseña' 
              style={{ display: 'block', width: '100%', padding: '12px 14px', boxSizing: 'border-box', backgroundColor: '#0f172a', border: '1px solid #334155', borderRadius: '8px', color: '#f8fafc', fontSize: '14px', outline: 'none' }} 
            />
          </div>

          <button 
            type='submit' 
            style={{ width: '100%', padding: '12px', backgroundColor: '#f97316', color: '#fff', border: 'none', borderRadius: '8px', fontWeight: '600', fontSize: '15px', cursor: 'pointer', transition: 'background 0.2s', marginBottom: '16px' }}
          >
            Entrar
          </button>

          {errorLogin && <p style={{ color: '#fca5a5', fontSize: '13px', marginBottom: '12px' }}>{errorLogin}</p>}
          <small style={{ color: '#94a3b8', fontSize: '12px', display: 'block', textAlign: 'center' }}>Demo local: admin / admin123</small>
        </form>
      </main>
    )
  }

  return (
    <div style={{ display: 'flex', width: '100vw', height: '100vh', fontFamily: "'Inter', sans-serif", backgroundColor: '#0f172a', color: '#f8fafc', overflow: 'hidden' }}>
      {/* Barra Lateral (Sidebar) Estilo Urbano */}
      <aside style={{ width: '280px', backgroundColor: '#090d16', borderRight: '1px solid #1e293b', display: 'flex', flexDirection: 'column', height: '100%' }}>
        <div style={{ padding: '24px 20px', fontSize: '22px', fontWeight: '800', letterSpacing: '0.5px', color: '#f97316', display: 'flex', alignItems: 'center', gap: '10px', borderBottom: '1px solid #1e293b' }}>
          <span>SKATES</span> <span style={{ color: '#f8fafc', fontSize: '14px', backgroundColor: '#1e293b', padding: '2px 8px', borderRadius: '4px' }}>ADMIN</span>
        </div>
        
        <nav style={{ padding: '20px 15px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <button 
            onClick={() => setActiveTab('products')}
            style={{
              padding: '14px 16px',
              textAlign: 'left',
              backgroundColor: activeTab === 'products' ? '#f97316' : 'transparent',
              color: activeTab === 'products' ? '#fff' : '#94a3b8',
              border: 'none',
              borderRadius: '8px',
              cursor: 'pointer',
              fontWeight: '600',
              fontSize: '15px',
              display: 'flex',
              alignItems: 'center',
              gap: '10px'
            }}
          >
            📦 Gestión de Productos
          </button>
          
          <button 
            onClick={() => setActiveTab('orders')}
            style={{
              padding: '14px 16px',
              textAlign: 'left',
              backgroundColor: activeTab === 'orders' ? '#f97316' : 'transparent',
              color: activeTab === 'orders' ? '#fff' : '#94a3b8',
              border: 'none',
              borderRadius: '8px',
              cursor: 'pointer',
              fontWeight: '600',
              fontSize: '15px',
              display: 'flex',
              alignItems: 'center',
              gap: '10px'
            }}
          >
            📋 Órdenes y Pedidos
          </button>
        </nav>
      </aside>

      {/* Contenido Principal */}
      <main style={{ flex: 1, padding: '40px', height: '100vh', overflowY: 'auto', backgroundColor: '#0f172a', display: 'flex', flexDirection: 'column' }}>
        <header style={{ marginBottom: '30px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h1 style={{ margin: 0, fontSize: '28px', fontWeight: '700', color: '#f8fafc' }}>
              {activeTab === 'products' ? 'Panel de Productos' : 'Panel de Órdenes'}
            </h1>
            <p style={{ margin: '5px 0 0 0', color: '#94a3b8', fontSize: '14px' }}>
              Inventario en H2 y pedidos del negocio de skate.
            </p>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', backgroundColor: '#1e293b', padding: '6px 14px', borderRadius: '20px', border: '1px solid #334155' }}>
            <span style={{ width: '8px', height: '8px', backgroundColor: '#22c55e', borderRadius: '50%', display: 'inline-block' }}></span>
            <button 
              type='button' 
              onClick={() => { cerrarSesion(); setAutenticado(false) }} 
              style={{ fontSize: '13px', padding: '4px 10px', background: 'transparent', border: 'none', color: '#94a3b8', cursor: 'pointer', fontWeight: '600' }}
            >
              Salir
            </button>
          </div>
        </header>

        <section style={{ backgroundColor: '#1e293b', padding: '30px', borderRadius: '12px', border: '1px solid #334155', boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.3)', flex: 1 }}>
          {activeTab === 'products' ? (
            <div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '16px', marginBottom: '24px' }}>
                <div style={{ backgroundColor: '#0f172a', border: '1px solid #334155', borderRadius: '10px', padding: '18px' }}>
                  <small style={{ color: '#94a3b8' }}>Stock total</small>
                  <h3 style={{ margin: '10px 0 0', fontSize: '28px', color: '#f8fafc' }}>{resumenStock.totalStock}</h3>
                </div>
                <div style={{ backgroundColor: '#0f172a', border: '1px solid #334155', borderRadius: '10px', padding: '18px' }}>
                  <small style={{ color: '#94a3b8' }}>Modelos</small>
                  <h3 style={{ margin: '10px 0 0', fontSize: '28px', color: '#f8fafc' }}>{resumenStock.modelos}</h3>
                </div>
                <div style={{ backgroundColor: '#0f172a', border: '1px solid #334155', borderRadius: '10px', padding: '18px' }}>
                  <small style={{ color: '#94a3b8' }}>Stock bajo</small>
                  <h3 style={{ margin: '10px 0 0', fontSize: '28px', color: '#f8fafc' }}>{resumenStock.bajoStock}</h3>
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                <h3 style={{ margin: 0, fontSize: '18px', color: '#f8fafc' }}>Inventario guardado en H2</h3>
                <button 
                  onClick={abrirModalCrear}
                  style={{ backgroundColor: '#f97316', color: '#fff', border: 'none', padding: '10px 18px', borderRadius: '8px', cursor: 'pointer', fontWeight: '600' }}
                >
                  + Agregar Producto
                </button>
              </div>

              <p style={{ color: '#94a3b8', fontSize: '14px' }}>{error ?? `${skates.length} productos cargados desde el backend.`}</p>

              <div style={{ marginTop: '24px', overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', color: '#f8fafc' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid #334155' }}>
                      <th style={{ textAlign: 'left', padding: '12px 10px', color: '#94a3b8' }}>ID</th>
                      <th style={{ textAlign: 'left', padding: '12px 10px', color: '#94a3b8' }}>Modelo</th>
                      <th style={{ textAlign: 'left', padding: '12px 10px', color: '#94a3b8' }}>Marca</th>
                      <th style={{ textAlign: 'left', padding: '12px 10px', color: '#94a3b8' }}>Medida</th>
                      <th style={{ textAlign: 'left', padding: '12px 10px', color: '#94a3b8' }}>Wheelbase</th>
                      <th style={{ textAlign: 'left', padding: '12px 10px', color: '#94a3b8' }}>Stock</th>
                      <th style={{ textAlign: 'center', padding: '12px 10px', color: '#94a3b8' }}>Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    {skates.map((skate) => (
                      <tr key={skate.id} style={{ borderBottom: '1px solid rgba(148, 163, 184, 0.2)' }}>
                        <td style={{ padding: '12px 10px' }}>{skate.id}</td>
                        <td style={{ padding: '12px 10px' }}>{skate.modelo}</td>
                        <td style={{ padding: '12px 10px' }}>{skate.marca}</td>
                        <td style={{ padding: '12px 10px' }}>{skate.medida ?? '—'}</td>
                        <td style={{ padding: '12px 10px' }}>{skate.wheelbase ?? '—'}</td>
                        <td style={{ padding: '12px 10px', color: skate.stock <= 5 ? '#fca5a5' : '#86efac', fontWeight: 700 }}>{skate.stock}</td>
                        <td style={{ padding: '12px 10px', textAlign: 'center' }}>
                          <button 
                            onClick={() => abrirModalEditar(skate)}
                            style={{ marginRight: '8px', padding: '6px 10px', backgroundColor: '#3b82f6', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '12px' }}
                          >
                            Editar
                          </button>
                          <button 
                            onClick={() => eliminarProducto(skate.id)}
                            style={{ padding: '6px 10px', backgroundColor: '#ef4444', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '12px' }}
                          >
                            Eliminar
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ) : (
            <div>
              <h3 style={{ margin: '0 0 10px 0', fontSize: '18px', color: '#f8fafc' }}>Historial de Órdenes de Clientes</h3>
              <p style={{ color: '#94a3b8', fontSize: '14px', marginBottom: '24px' }}>Gestiona y actualiza el estado de las órdenes y envíos.</p>

              <div style={{ display: 'grid', gap: '16px' }}>
                {pedidos.map((pedido) => (
                  <article key={pedido.id} style={{ backgroundColor: '#0f172a', border: '1px solid #334155', borderRadius: '10px', padding: '18px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '16px', marginBottom: '12px' }}>
                      <div>
                        <small style={{ color: '#f97316' }}>Pedido #{pedido.id}</small>
                        <h4 style={{ margin: '6px 0 0', fontSize: '18px', color: '#f8fafc' }}>{pedido.cliente}</h4>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontSize: '13px', color: '#94a3b8' }}>Estado:</span>
                        <select 
                          value={pedido.estado} 
                          onChange={(e) => cambiarEstadoPedido(pedido.id, e.target.value as any)}
                          style={{ backgroundColor: '#1e293b', color: '#fff', border: '1px solid #334155', padding: '6px 10px', borderRadius: '6px', outline: 'none' }}
                        >
                          <option value="Pendiente">Pendiente</option>
                          <option value="Enviado">Enviado</option>
                          <option value="Entregado">Entregado</option>
                        </select>
                      </div>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '12px', color: '#cbd5e1' }}>
                      <div><strong>Producto:</strong><br />{pedido.producto}</div>
                      <div><strong>Cantidad:</strong><br />{pedido.cantidad}</div>
                      <div><strong>Fecha:</strong><br />{pedido.fecha}</div>
                      <div><strong>Total:</strong><br />${pedido.total.toLocaleString('es-CL')}</div>
                    </div>
                  </article>
                ))}
              </div>
            </div>
          )}
        </section>
      </main>

      {/* Modal para Crear / Editar Producto */}
      {modalAbierto && (
        <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.7)', display: 'grid', placeItems: 'center', zIndex: 100 }}>
          <form onSubmit={guardarProducto} style={{ backgroundColor: '#1e293b', padding: '30px', borderRadius: '12px', width: 'min(450px, 90vw)', border: '1px solid #334155' }}>
            <h3 style={{ margin: '0 0 20px 0', color: '#f8fafc' }}>{modoEdicion ? 'Editar Producto' : 'Nuevo Producto'}</h3>
            
            <div style={{ marginBottom: '14px' }}>
              <label style={{ display: 'block', fontSize: '13px', color: '#94a3b8', marginBottom: '6px' }}>Modelo</label>
              <select value={modelo} onChange={(e) => setModelo(e.target.value)} style={{ width: '100%', padding: '10px', backgroundColor: '#0f172a', color: '#fff', border: '1px solid #334155', borderRadius: '6px' }}>
                <option value="Street">Street</option>
                <option value="Longboard">Longboard</option>
                <option value="Downhill">Downhill</option>
              </select>
            </div>

            <div style={{ marginBottom: '14px' }}>
              <label style={{ display: 'block', fontSize: '13px', color: '#94a3b8', marginBottom: '6px' }}>Marca</label>
              <input required value={marca} onChange={(e) => setMarca(e.target.value)} placeholder="Ej. Independent" style={{ width: '100%', padding: '10px', boxSizing: 'border-box', backgroundColor: '#0f172a', color: '#fff', border: '1px solid #334155', borderRadius: '6px' }} />
            </div>

            <div style={{ marginBottom: '14px' }}>
              <label style={{ display: 'block', fontSize: '13px', color: '#94a3b8', marginBottom: '6px' }}>Medida (opcional)</label>
              <input type="number" step="0.1" value={medida} onChange={(e) => setMedida(e.target.value)} placeholder="Ej. 8.0" style={{ width: '100%', padding: '10px', boxSizing: 'border-box', backgroundColor: '#0f172a', color: '#fff', border: '1px solid #334155', borderRadius: '6px' }} />
            </div>

            <div style={{ marginBottom: '14px' }}>
              <label style={{ display: 'block', fontSize: '13px', color: '#94a3b8', marginBottom: '6px' }}>Wheelbase (opcional)</label>
              <input type="number" step="0.01" value={wheelbase} onChange={(e) => setWheelbase(e.target.value)} placeholder="Ej. 20.5" style={{ width: '100%', padding: '10px', boxSizing: 'border-box', backgroundColor: '#0f172a', color: '#fff', border: '1px solid #334155', borderRadius: '6px' }} />
            </div>

            <div style={{ marginBottom: '20px' }}>
              <label style={{ display: 'block', fontSize: '13px', color: '#94a3b8', marginBottom: '6px' }}>Stock inicial</label>
              <input type="number" required value={stock} onChange={(e) => setStock(e.target.value)} style={{ width: '100%', padding: '10px', boxSizing: 'border-box', backgroundColor: '#0f172a', color: '#fff', border: '1px solid #334155', borderRadius: '6px' }} />
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <button type="button" onClick={() => setModalAbierto(false)} style={{ padding: '10px 16px', backgroundColor: '#334155', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer' }}>Cancelar</button>
              <button type="submit" style={{ padding: '10px 16px', backgroundColor: '#f97316', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600 }}>Guardar</button>
            </div>
          </form>
        </div>
      )}
    </div>
  )
}